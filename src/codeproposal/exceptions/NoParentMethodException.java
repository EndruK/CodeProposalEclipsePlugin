package codeproposal.exceptions;

public class NoParentMethodException extends Exception {
	private static final long serialVersionUID = 2L;
	public NoParentMethodException() {
		super("Node has no parent Method!");
	}
	public NoParentMethodException(String message) {
		super(message);
	}
}
