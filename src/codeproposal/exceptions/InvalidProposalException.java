package codeproposal.exceptions;

public class InvalidProposalException extends Exception {
	private static final long serialVersionUID = 1L;
	public InvalidProposalException() {
		super("The Proposal cannot be created");
	}
	public InvalidProposalException(String message) {
		super(message);
	}
}
