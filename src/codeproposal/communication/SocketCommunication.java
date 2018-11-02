package codeproposal.communication;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class SocketCommunication {
	private static final String HOSTNAME = "localhost";
	private static final int PORT = 8912;
	private static final String SHARE_FILE_PATH = "/tmp/response";
	private static final String INCOMING_SIGNAL = "<DONE>";
	private static final String OUTGOING_SIGNAL = "<INC>";
	private static final String FAIL_SIGNAL = "<FAIL>";
	
	private Socket socket;
	private DataOutputStream dout;
	private DataInputStream din;
	
	/**
	 * Initialize socket connection and both Input and Output Streams
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public SocketCommunication() throws UnknownHostException, IOException {
		this.socket = new Socket(HOSTNAME, PORT);
		this.dout = new DataOutputStream(this.socket.getOutputStream());
		this.din = new DataInputStream(this.socket.getInputStream());
	}
	
	/**
	 * read a message from the Data Input Stream and handle the content of the message
	 * such as read the Python response from the shared file or prevent exceptions
	 * @return
	 * @throws IOException
	 */
	public String getMessage() throws IOException {
		String response = this.din.readUTF();
		String generated_json = "";
		if(response.equals(SocketCommunication.INCOMING_SIGNAL)) {
			generated_json = readFromFile();
			generated_json = replaceSingleQuote(generated_json);
		}
		else if(response.equals(SocketCommunication.FAIL_SIGNAL)) {
			System.out.println("Python FAILURE");
			return "";
		}
		else {
			System.out.println("SOMETHING COMPLETELY ELSE");
		}
		return generated_json;
	}
	
	/**
	 * Read the content of the defined shared file and return it
	 * @return
	 */
	private String readFromFile() {
		BufferedReader bReader = null;
		FileReader fReader = null;
		String allLines = "";
		try {
			fReader = new FileReader(SocketCommunication.SHARE_FILE_PATH);
			bReader = new BufferedReader(fReader);
			String line = "";
			while((line = bReader.readLine()) != null) {
				allLines += line + "\n";
			}
		} catch(IOException e) {
			e.printStackTrace();
		} finally {
			try {
			if (bReader != null) bReader.close();
			if (fReader != null) fReader.close();
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		return allLines;
	}
	
	/**
	 * Send a message to the Python server via Data Output Stream
	 * @param message
	 * @throws IOException
	 */
	public void sendMessage(String message) throws IOException {
		writeToFile(message);this.dout.writeUTF(SocketCommunication.OUTGOING_SIGNAL);
		this.dout.flush();
	}
	
	/**
	 * Write the given String to the shared file
	 * @param message
	 * @throws FileNotFoundException
	 */
	private void writeToFile(String message) throws FileNotFoundException {
		PrintWriter writer = new PrintWriter(SocketCommunication.SHARE_FILE_PATH);
		writer.println(message);
		writer.close();
	}
	
	/**
	 * Close all connections
	 * @throws IOException
	 */
	public void closeConnection() throws IOException {
		this.dout.close();
		this.din.close();
		this.socket.close();
	}
	
	/**
	 * Print the info of the socket connection
	 */
	public static void printInfo() {
		System.out.println("Hostname: " + HOSTNAME);
		System.out.println("Port: " + PORT);
	}
	
	/**
	 * Replace all single quotes of a response String with double Quotes to get a valid JSON String
	 * @param in
	 * @return
	 */
	private String replaceSingleQuote(String in) {
		return in.replaceAll("'", "\"");
	}
}
