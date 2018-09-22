package activitystreamer.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.server.Connection;
import activitystreamer.server.Control;
import activitystreamer.util.Settings;

public class ClientSkeleton extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ClientSkeleton clientSolution;
	private TextFrame textFrame;
	private Socket socket;
	private DataInputStream in;
	private DataOutputStream out;
	private BufferedReader inreader;
	private PrintWriter outwriter;
	private boolean term = false;
	private JSONParser parser = new JSONParser();

	
	public static ClientSkeleton getInstance(){
		if(clientSolution==null){
			clientSolution = new ClientSkeleton();
		}
		return clientSolution;
	}
	
	@SuppressWarnings("unchecked")
	public ClientSkeleton(){
		
		//Zhenyuan
			try { 
				if (!term) {             //xueyang
				socket = new Socket(Settings.getRemoteHostname(),Settings.getRemotePort());
				in = new DataInputStream(socket.getInputStream());
			    out = new DataOutputStream(socket.getOutputStream());
			    inreader = new BufferedReader(new InputStreamReader(in));
			    outwriter = new PrintWriter(out, true);
			    JSONObject outcomingObj = new JSONObject();
			    
			    if(Settings.getUsername().equals("anonymous")) {
			    	outcomingObj.put("command", "LOGIN");
			    	outcomingObj.put("username", Settings.getUsername());
			    }
			    
			    else if(Settings.getSecret() == null) {
			    	outcomingObj.put("command", "REGISTER");
			    	outcomingObj.put("username", Settings.getUsername());
			    	Settings.setSecret(Settings.nextSecret());
			    	outcomingObj.put("secret", Settings.getSecret());
			    	
			    }else {
			    	outcomingObj.put("command", "LOGIN");
			    	outcomingObj.put("username", Settings.getUsername());
			    	outcomingObj.put("secret", Settings.getSecret());
			    }
			    	outwriter.println(outcomingObj.toString());
			    	outwriter.flush(); 
			    
			}
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		//Zhenyuan
		
		textFrame = new TextFrame();
		start();
	}
	
	
	public void sendActivityObject(JSONObject activityObj){
		//Zhenyuan
		if(activityObj.get("command").equals("LOGOUT")) {
			term = true;
			log.info("log out");
		}
		outwriter.println(activityObj.toJSONString());
		outwriter.flush(); 
		//Zhenyuan
	}
	
	//Xueyang
	public void disconnect(){
		if(!term){
			log.info("closing connection "+Settings.socketAddress(socket));
			try {
				term=true;
				outwriter.close();
				inreader.close();
				socket.close();
			} catch (IOException e) {
				log.error("received exception closing the connection "+Settings.socketAddress(socket)+": "+e);
			}
		}
	}
	//Xueyang
	
	@SuppressWarnings("unchecked")
	public void run(){
		while(!term) {
		JSONObject incomingObj;
		try {
			incomingObj = (JSONObject) parser.parse(inreader.readLine());
			this.textFrame.setOutputText(incomingObj);
			String command = (String) incomingObj.get("command");
			
			if(command.equals("INVALID_MESSAGE")) {
				clientSolution.disconnect();
			}		
			
			if(command.equals("REDIRECT")) {
				log.info("I received the redirect message from server!!!");
				Settings.setRemoteHostname(incomingObj.get("hostname").toString());
				Settings.setRemotePort(Integer.parseInt(incomingObj.get("port").toString()));
				clientSolution.disconnect();
				log.info("\nclient redirect\n");
				clientSolution = new ClientSkeleton();
			}
			
			if(command.equals("REGISTER_SUCCESS")) {
				log.info("\n\nregister success\n");
				JSONObject outcomingObj= new JSONObject();
				outcomingObj.put("command", "LOGIN");
		    	outcomingObj.put("username", Settings.getUsername());
		    	outcomingObj.put("secret", Settings.getSecret());
		    	outwriter.println(outcomingObj.toJSONString());
				outwriter.flush(); 
			}
			
			if(command.equals("LOGIN_SUCCESS")) {
				log.info("\n\nlogin success\n");
			}
		
			if(command.equals("REGISTER_FAILED") || command.equals("LOGIN_FAILED") || command.equals("AUTHENTICATION_FAIL")) {
				clientSolution.disconnect();
				log.info(incomingObj.get("info").toString());
			}
			
		} catch (NullPointerException e) {
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		}
	}
	
}
