package codeproposal.ast;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.serialization.JavaParserJsonDeserializer;
import com.github.javaparser.serialization.JavaParserJsonSerializer;

import codeproposal.exceptions.InvalidProposalException;
import codeproposal.exceptions.NoParentMethodException;
//https://stackoverflow.com/questions/9667615/how-do-i-get-the-current-method-from-the-active-eclipse-editor
//https://stackoverflow.com/questions/1636131/ast-for-current-selected-code-in-eclipse-editor
public class ASTHandler {
	private static final String INVOCATION = "Object CodeProposalInvocationMarker;";
	private static final String INVOCATION_REPLACEMENT = "{<INV>:<EMPTY>}";
	/**
	 * Generate a JSON String which is required by the python script
	 * It needs a <INV>:<EMPTY> token at the position of the proposal invocation
	 * @param context
	 * @param monitor
	 * @return
	 * @throws InvalidProposalException
	 */
	public String generateCommunicationString(ContentAssistInvocationContext context,
			IProgressMonitor monitor) throws InvalidProposalException {
		IEditorPart editor = getEditor();
		ITextSelection selection = getSelection(editor);
		// get the invocation position in the editor
		int invocationOffset = selection.getOffset();
		
		String plaintext = "";
		// try to get the plain text in the current editor
		try {
			plaintext = getEditorPlainText(editor);
		} catch (JavaModelException e1) {
			throw new InvalidProposalException("cannot get plain text from editor");
		}
		// inject special string to search for later
		String modifiedPlainText = injectInvocationPointToPlainText(plaintext, invocationOffset);
		// get AST of modified plain Text
		ASTParser parser = ASTParser.newParser(AST.JLS10);
		parser.setSource(modifiedPlainText.toCharArray());
		parser.setResolveBindings(true);
		CompilationUnit cu = (CompilationUnit) parser.createAST(monitor);
		ASTNode invocationNode = getASTNodeAtCaret(cu, selection);
		//try to find the parent Method
		MethodDeclaration parentMethod = null;
		try {
			parentMethod = getParentMethod(invocationNode);
		} catch(NoParentMethodException e) {
			throw new InvalidProposalException("cannot get parent method at invocation");
		}
		//convert to JSON string
		String parentMethodJSON = getJPMethodJSON(parentMethod);
		// get JSON version of INVOCATION snippet
		String invocationJSON = getInvocationJSON();
		// replace special string with expected <INV>:<EMPTY> token
		String finalMethodJSON = replaceInvocation(parentMethodJSON, invocationJSON);
		return finalMethodJSON;
	}
	
	/**
	 * translate a JP JSON String back to normal code
	 * @param json
	 * @return
	 */
	public static String jsonToCode(String json) {
		JavaParserJsonDeserializer deserializer = new JavaParserJsonDeserializer();
		Node node = deserializer.deserializeObject(Json.createReader(new StringReader(json)));
		return node.toString();
	}
	
	/**
	 * replace invocationJSON in methodJSON with INVOCATION_REPLACEMENT
	 * @param methodJSON
	 * @param invocationJSON
	 * @return
	 */
	private String replaceInvocation(String methodJSON, String invocationJSON) {
		int pos = methodJSON.indexOf(invocationJSON);
		String result = methodJSON.substring(0, pos)
				+ ASTHandler.INVOCATION_REPLACEMENT
				+ methodJSON.substring(pos+invocationJSON.length(), methodJSON.length());
		return result;
	}
	
	/**
	 * get the defined invocation as a JavaParser element serialized as JSON
	 * @return
	 */
	private String getInvocationJSON() {
		ASTParser parser = ASTParser.newParser(AST.JLS10);
		parser.setSource(ASTHandler.INVOCATION.toCharArray());
		parser.setResolveBindings(true);
		parser.setKind(ASTParser.K_STATEMENTS);
		Block block = (Block)parser.createAST(null);
		@SuppressWarnings("unchecked")
		List<Statement> statements = block.statements();
		Statement first = statements.get(0);
		VariableDeclarationStatement invocation = (VariableDeclarationStatement)first;
		return getJPVarExprJSON(invocation);
	}
	
	/**
	 * tries to get the next method declaration above current node
	 * @param node
	 * @return
	 * @throws NoParentMethodException
	 */
	private MethodDeclaration getParentMethod(ASTNode node) throws NoParentMethodException {
		// check if we have a compilation unit
		if(!(node instanceof CompilationUnit)) {
			ASTNode parent = node.getParent();
			// check if the parent is of type MethodDeclaration
			if(parent instanceof MethodDeclaration) {
				return (MethodDeclaration) parent;
			}
			else {
				//recursion - walk the tree up
				return getParentMethod(parent);
			}
		}
		// no parent method found - throw an exception
		throw new NoParentMethodException();
	}
	
	/**
	 * find the AST node at the text selection
	 * @param cu
	 * @param selection
	 * @return
	 */
	private ASTNode getASTNodeAtCaret(CompilationUnit cu, ITextSelection selection) {
		// get the current node we are in
		NodeFinder finder = new NodeFinder(cu, selection.getOffset(), selection.getLength());
		ASTNode node = finder.getCoveringNode();
		return node;
	}
	
	/**
	 * get the editor which is currently opened
	 * @return
	 */
	private IEditorPart getEditor() {
		// get the activated workbench
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		// get editor in workbench
		IEditorPart editor = page.getActiveEditor();
		return editor;
	}
	
	/**
	 * get the selected part inside an editor
	 * @param editor
	 * @return
	 */
	private ITextSelection getSelection(IEditorPart editor) {
		IEditorSite site = editor.getEditorSite();
		ITextSelection selection = (ITextSelection) site.getSelectionProvider().getSelection();
		return selection;
	}
	
	/**
	 * extract the compilation unit of the opened file as JDT Compilation Unit
	 * @param editor
	 * @param monitor
	 * @return
	 */
//	private CompilationUnit getCompilationUnit(IEditorPart editor, IProgressMonitor monitor) {
//		ITypeRoot typeRoot = JavaUI.getEditorInputTypeRoot(editor.getEditorInput());
//		ICompilationUnit unit = (ICompilationUnit) typeRoot.getAdapter(ICompilationUnit.class);
//		// get AST of java file
//		ASTParser parser = ASTParser.newParser(AST.JLS10);
//		parser.setSource(unit);
//		parser.setResolveBindings(true);
//		CompilationUnit cu = (CompilationUnit) parser.createAST(monitor);
//		return cu;
//	}
	
	/**
	 * get the current plain text of the given editor
	 * @param editor
	 * @return
	 * @throws JavaModelException
	 */
	private String getEditorPlainText(IEditorPart editor) throws JavaModelException {
		ITypeRoot typeRoot = JavaUI.getEditorInputTypeRoot(editor.getEditorInput());
		ICompilationUnit unit = (ICompilationUnit) typeRoot.getAdapter(ICompilationUnit.class);
		return unit.getSource();
	}
	
	/**
	 * inject special code snippet to given plain text
	 * @param plainText
	 * @param offset
	 * @return
	 */
	private String injectInvocationPointToPlainText(String plainText, int offset) {
		return plainText.substring(0, offset)
				+ ASTHandler.INVOCATION
				+ plainText.substring(offset, plainText.length()-1);
	}
	
	/**
	 * get JavaParser JSON serialization of a given JDT Statement
	 * @param stmt
	 * @return
	 */
//	private String getJPStatementJSON(Statement stmt) {
//		// cast JDT AST Node to JavaParser AST Node
//		com.github.javaparser.ast.stmt.Statement jpStmt = jdtStatementToJPStatement(stmt);
//		// transform this into JSON
//		return serializeJPNode(jpStmt);
//	}
	
	/**
	 * get JavaParser JSON serialization of a given arbitrary JDT AST node
	 * @param node
	 * @return
	 */
//	private String getJPNodeJSON(ASTNode node) {
//		com.github.javaparser.ast.Node jpNode = jdtNodeToJPNode(node);
//		return serializeJPNode(jpNode);
//	}
	
	/**
	 * get JavaParser JSON serialization of a given JDT VariableDeclarationStatement
	 * @param stmt
	 * @return
	 */
	private String getJPVarExprJSON(VariableDeclarationStatement stmt) {
		com.github.javaparser.ast.stmt.Statement jpStmt = jdtStatementToJPStatement(stmt);
		return serializeJPNode(jpStmt);
	}
	
	/**
	 * get JavaParser JSON serialization of a given JDT MethodDeclaration
	 * @param method
	 * @return
	 */
	private String getJPMethodJSON(MethodDeclaration method) {
		// cast JDT AST Node to JavaParser AST Node
		com.github.javaparser.ast.body.MethodDeclaration jpMethod = jdtMethodToJPMethod(method);
		// transform this into JSON
		return serializeJPNode(jpMethod);
	}
	
	/**
	 * Serialize any given JavaParser AST node
	 * @param node
	 * @return
	 */
	private String serializeJPNode(Node node) {
		Map<String, ?> config = new HashMap<>();
        JsonGeneratorFactory generatorFactory = Json.createGeneratorFactory(config);
        JavaParserJsonSerializer serializer = new JavaParserJsonSerializer();
        StringWriter jsonWriter = new StringWriter();
        try (JsonGenerator generator = generatorFactory.createGenerator(jsonWriter)) {
            serializer.serialize(node, generator);
        }
		return jsonWriter.toString();
	}
	
	/**
	 * cast arbitrary JDT AST node to JavaParser AST node
	 * @param node
	 * @return
	 */
//	private com.github.javaparser.ast.Node jdtNodeToJPNode(ASTNode node) {
//		com.github.javaparser.ast.Node jpNode = JavaParser.parse(node.toString());
//		return jpNode;
//	}
	
	/**
	 * cast JDT node to JavaParser AST MethodDeclaration
	 * @param node
	 * @return
	 */
	private com.github.javaparser.ast.body.MethodDeclaration jdtMethodToJPMethod(ASTNode node) {
		com.github.javaparser.ast.body.MethodDeclaration decl = (com.github.javaparser.ast.body.MethodDeclaration) JavaParser.parseBodyDeclaration(node.toString());
		return decl;
	}
	
	/**
	 * cast JDT node to JavaParser AST Statement
	 * @param node
	 * @return
	 */
	private com.github.javaparser.ast.stmt.Statement jdtStatementToJPStatement(ASTNode node) {
		com.github.javaparser.ast.stmt.Statement stmt = (com.github.javaparser.ast.stmt.Statement) JavaParser.parseStatement(node.toString());
		return stmt;
	}
}
