package codeproposal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import codeproposal.ast.ASTHandler;
import codeproposal.communication.SocketCommunication;
import codeproposal.exceptions.InvalidProposalException;

public class ProposalComputer implements IJavaCompletionProposalComputer{
	private SocketCommunication comm;
	private ASTHandler astHandler;
	
	public ProposalComputer() {
		super();
		this.astHandler = new ASTHandler();
	}
	
	@Override
	public void sessionStarted() {}
	
	/**
	 * Generate a List of Auto-Completion Proposals
	 * @param context
	 * @param monitor
	 * @return
	 */
	@Override
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		List<ICompletionProposal> res = new ArrayList<ICompletionProposal>();
		try {
			// establish a new connection
			this.comm = new SocketCommunication();
			//get the NN input String
			String pythonInput = astHandler.generateCommunicationString(context, monitor);
			// send a message to python server that new invocation is available
			comm.sendMessage(pythonInput);
			// get the response of the python script
			String response = comm.getMessage();
			// if the response is empty: break out
			if(response.length() == 0) {
				return res;
			}
			String code = ASTHandler.jsonToCode(response);
			res.add(new ICompletionProposal() {
				
				@Override
				public Point getSelection(IDocument document) {
					return null;
				}
				
				@Override
				public Image getImage() {
					return null;
				}
				
				@Override
				public String getDisplayString() {
					return code;
				}
				
				@Override
				public IContextInformation getContextInformation() {
					return null;
				}
				
				@Override
				public String getAdditionalProposalInfo() {
					return null;
				}
				
				@Override
				public void apply(IDocument document) {
					// get the position in the document
					int position = context.getViewer().getSelectedRange().x;
					// get the text in the current document
					String curText = document.get();
					// get the offset of the proposal invocation
					int index = context.getInvocationOffset();
					// all text before the invocation
					String before = curText.substring(0, index);
					// all text after the invocation
					String after  = curText.substring(index);
					document.set(before + getDisplayString() + after);
					context.getViewer().setSelectedRange(position + getDisplayString().length() + 1, -1);
				}
			});
		} catch(IOException | InvalidProposalException e) {
			e.printStackTrace();
		}
		return res;
	}
	
	@Override
	public List<IContextInformation> computeContextInformation(ContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		return new ArrayList<IContextInformation>();
	}
	
	@Override
	public String getErrorMessage() {
		return "Error during Completion!";
	}
	
	@Override
	public void sessionEnded() {
		try {
			comm.closeConnection();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
