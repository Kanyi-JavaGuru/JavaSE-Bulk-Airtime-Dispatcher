package co.ke.xyz;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Dispatcher {

	private static String FILE_NAME = "airtime_recipients.csv";
	private static final File FILE_OF_RECIPIENTS = new File(FILE_NAME);
	
	//array of non-duplicate marketeers
	private static final Set<Marketeer> MARKETEERS = new HashSet<>();
	
	private static String URL = "https://api.africastalking.com/version1/airtime/send";
	private static String USERNAME = "";//your username for africas talking here
	private static String APIKEY = "";//your apiKey from africas talking here
	
	//Array to hold recipients to be sent to Africas talking
	private static List<String> RECIPIENTS = new ArrayList<>();
	
	//lock to ensure synchronization of threads
	private static final Object LOCK = new Object();
	
	public static void main(String[] args) {
		ExecutorService executeInputProcessing = Executors.newFixedThreadPool(4);
		ExecutorService executeOutputProcessing = Executors.newFixedThreadPool(4);
		try {
			Scanner scan = new Scanner(FILE_OF_RECIPIENTS);
			//skip the header
			scan.nextLine();
			while(scan.hasNext()) {
				//read recipient details from csv file
				String rawRecipientDetails = scan.nextLine();
				executeInputProcessing.submit(new InputProcessor(rawRecipientDetails, MARKETEERS, LOCK));
			}
			scan.close();
			//shutdown executor service
			executeInputProcessing.shutdown();
			//await for all processing to finish
			executeInputProcessing.awaitTermination(1, TimeUnit.MINUTES);
			//process the marketeer details to send to africas talking
			for(Marketeer marketeer: MARKETEERS) {
				System.out.println(marketeer);
				executeOutputProcessing.submit(new OutPutProcessor(marketeer, RECIPIENTS, LOCK));
			}
			//shutdown executor service
			executeOutputProcessing.shutdown();
			//await for all processing to finish
			executeOutputProcessing.awaitTermination(1, TimeUnit.MINUTES);
			//Send airtime to recipients through africas talking api
			AirtimeDispatcher dispatcher = new AirtimeDispatcher(URL, USERNAME, APIKEY, RECIPIENTS);
			//print out the response
			System.out.println("Response: "+dispatcher.sendAirtimeHttpRequest());
		} catch (FileNotFoundException e) {
			System.out.println("File: "+FILE_NAME+" Not Found. Error message: "+e.getMessage());
		} catch (InterruptedException e) {
			System.out.println("Thread Error: "+e.getMessage());
		}
	}

}

class AirtimeDispatcher{
	private final String url;
	private final String username;
	private final String apiKey;
	private final List<String> recipientsList;
	
	public AirtimeDispatcher(String url, String username, String apiKey, final List<String> recipientsList) {
		this.url = url;
		this.username = username;
		this.apiKey = apiKey;
		this.recipientsList = recipientsList;
	}
	public String sendAirtimeHttpRequest() {
		String response = null;		
	    BufferedReader reader;
		
		try {			
			HashMap<String, Object> data = new HashMap<String, Object>();			
	    	StringBuffer formData = new StringBuffer();		
	    	
	    	StringBuffer buffer = new StringBuffer();
	    	buffer.append("[");
	    	for(int i = 0; i<recipientsList.size(); i++) {	 
	    		buffer.append(recipientsList.get(i));
	    		if(i != recipientsList.size()-1)
		    		buffer.append(",");
	    	}
	    	buffer.append("]");
	    	//print out the recipients in Africas talking format
	    	System.out.println(buffer.toString());
	    	
	    	data.put("username", username);
	    	data.put("recipients", buffer.toString());
	    	
	    	Iterator<Entry<String, Object>> it = data.entrySet().iterator();
	    	while (it.hasNext()) {
	    	    Map.Entry<String, Object> pairs = (Map.Entry<String, Object>)it.next();
	    	    formData.append(URLEncoder.encode(pairs.getKey().toString(), "UTF-8"));
	    	    formData.append("=");
	    	    formData.append(URLEncoder.encode(pairs.getValue().toString(), "UTF-8"));
	    	    if ( it.hasNext() )
	    	    	formData.append("&");
	    	}
			
			URL urlObj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) urlObj.openConnection();
			con.setRequestProperty("Accept", "application/json");
			con.setRequestProperty("apikey", apiKey);
			con.setDoOutput(true);
			
			System.out.println("Waiting for response...");
			
			OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream());
		    writer.write(formData.toString());		    
		    writer.flush();
		    
		    int responseCode = con.getResponseCode();
		    
		    if(responseCode == 200 || responseCode == 201) 
		    	reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
		    else {
				reader = new BufferedReader(new InputStreamReader(con.getErrorStream()));
		    }
		    
		    StringBuilder builder = new StringBuilder();
		    String line;
		    while((line = reader.readLine()) != null) {
		    	builder.append(line);
			}

		    response = builder.toString();
		    
		    reader.close();		    
			
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return response;
	}
}

class Marketeer{
	private String name;
	private String countryCode; 
	private int phoneNo;
	private Double amount;
	
	public Marketeer(String name, String countryCode, int phoneNo, Double amount) {
		this.name = name;
		this.countryCode = countryCode;
		this.phoneNo = phoneNo;
		this.amount = amount;
	}

	@Override
	public int hashCode() {
		//compare using phoneNo for duplicates
		return phoneNo;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() !=obj.getClass())
			return false;
		Marketeer other = (Marketeer) obj;
		if(phoneNo != other.phoneNo)
			return false;
		return true;
	}

	public String getName() {
		return name;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public int getPhoneNo() {
		return phoneNo;
	}

	public Double getAmount() {
		return amount;
	}

	@Override
	public String toString() {
		return "Marketeer [name=" + name + ", countryCode=" + countryCode + ", phoneNo=" + phoneNo + ", amount="
				+ amount + "]";
	}
}

class Recipient{
	private String phoneNumber;
	private String amount;
	public Recipient(String phoneNumber, String amount) {
		this.phoneNumber = phoneNumber;
		this.amount = amount;
	}
	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("{\"amount\":\"");
		buffer.append(amount);
		buffer.append("\"");
		buffer.append(", ");
		buffer.append("\"phoneNumber\":\"");
		buffer.append("+"+phoneNumber);
		buffer.append("\"");
		buffer.append("}");
		
		return buffer.toString();
	}	
}

/**
 * Converts data from file
 * to Marketeer Object 
 * and adds it to list of Marketeers
 * Eliminating duplicates 
 * and removing invalid Phone Numbers 
 * **/
class InputProcessor implements Runnable{
	private final String rawRecipients;
	private final Set<Marketeer> listOfProcessedMarketeers;	
	private final Object lock;
	
	public InputProcessor(final String rawRecipients, final Set<Marketeer> listOfProcessedMarketeers, final Object lock) {
		this.rawRecipients = rawRecipients;
		this.listOfProcessedMarketeers = listOfProcessedMarketeers;
		this.lock = lock;
	}

	@Override
	public void run() {
		String[] marketeerDetails = rawRecipients.split(",");
		//get the name
		String name = marketeerDetails[0].trim(); 
		//get the phoneNo
		String rawPhoneNo = marketeerDetails[1].trim();
		//get the amount
		String rawAmount = marketeerDetails[2].trim();
		try {
			String countryCode = rawPhoneNo.substring(0, 3);
			Integer phoneNo = Integer.parseInt(rawPhoneNo.substring(3, 12));
			Float floatAmount = Float.parseFloat(rawAmount);
			//round off to the nearest upper whole number
			Double amount = Math.ceil(floatAmount);
			//check if phone number is 9 digits
			if(phoneNo.toString().length() != 9)
				System.out.println("Wrong phone No length for Marketeer "+name);
			else {
				//create Marketeer object
				Marketeer marketeer = new Marketeer(name, countryCode, phoneNo, amount);
				synchronized(lock) {
					//add to the marketeers array ensuring no duplicates are added
					listOfProcessedMarketeers.add(marketeer);
				}
			}
		} catch (NumberFormatException e) {
			System.out.println("Wrong format: "+e.getMessage()+": For Employee: "+name);
		}
	}	
}

/**
 * Converts Marketeer information from list
 * to Africas talking required format
 * **/
class OutPutProcessor implements Runnable{

	private final Marketeer marketeer;
	private final Object lock;
	private final List<String> recipients;

	public OutPutProcessor(final Marketeer marketeer, final List<String> recipients, final Object lock) {
		this.marketeer = marketeer;
		this.recipients = recipients;
		this.lock = lock;
	}
	@Override
	public void run() {	
		final StringBuffer fullPhoneNo = new StringBuffer();
		fullPhoneNo.append(marketeer.getCountryCode());
		fullPhoneNo.append(marketeer.getPhoneNo());
		
		final StringBuffer amount = new StringBuffer();
		amount.append("KES ");
		amount.append(marketeer.getAmount().intValue());
		
		Recipient recipient = new Recipient(fullPhoneNo.toString(), amount.toString());
		
		synchronized (lock) {
			recipients.add(recipient.toString());
		}		
	}
	
}

