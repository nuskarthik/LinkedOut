package nus.dtn.app.broadcast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import nus.cirl.piloc.DataCollectionService;
import nus.cirl.piloc.DataStruture.FPConf;
import nus.cirl.piloc.DataStruture.Fingerprint;
import nus.cirl.piloc.DataStruture.Radiomap;
import nus.dtn.api.fwdlayer.ForwardingLayerException;
import nus.dtn.api.fwdlayer.ForwardingLayerInterface;
import nus.dtn.api.fwdlayer.ForwardingLayerProxy;
import nus.dtn.api.fwdlayer.MessageListener;
import nus.dtn.middleware.api.DtnMiddlewareInterface;
import nus.dtn.middleware.api.DtnMiddlewareProxy;
import nus.dtn.middleware.api.MiddlewareEvent;
import nus.dtn.middleware.api.MiddlewareListener;
import nus.dtn.util.Descriptor;
import nus.dtn.util.DtnMessage;
import nus.dtn.util.DtnMessageException;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/** App that broadcasts messages to everyone using a Mobile DTN. */
@SuppressLint("NewApi")
public class BroadcastAppActivity extends Activity implements
		SensorEventListener {

	private boolean mIsLocation = false;
	private Radiomap mRadiomap = null;
	private String myID = "";
	private DataCollectionService mPilocService = null;
	AlertDialog.Builder builder;
	Point mCurrentLocation = null;
	Point mPreviousLocation = null;
	/** Called when the activity is first created. */
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {

			// Specify what GUI to use
			setContentView(R.layout.main);

			//Create and bind the piLoc Service
			Intent intent = new Intent(this, DataCollectionService.class);
			this.getApplicationContext().bindService(intent, conn, Context.BIND_AUTO_CREATE);

			// Set a handler to the current UI thread
			handler = new Handler();

			// Get references to the GUI widgets
			textView_Message = (TextView) findViewById(R.id.TextView_Message);
			joinButton = (Button) findViewById(R.id.JoinButton);
			myList = (ListView) findViewById(R.id.myList);
			myName = (EditText) findViewById(R.id.myName);

			values = new ArrayList<ListModel>();
			map = new HashMap<String, Boolean>();
			isTalking = false;
			isWalking = false;
			builder = new AlertDialog.Builder(this);
			ListModel[] testListModel = new ListModel[values.size()];
			for(int i=0;i<values.size();i++){
				testListModel[i] = values.get(i);
			}
			adapter = new CustomAdapter(this, values);
			myList.setAdapter(adapter);

			File fileDirectory = new File( Environment.getExternalStorageDirectory() ,
					"LinkedOut" );
			fileDirectory.mkdirs();
			if ( ! fileDirectory.isDirectory() )
				throw new IOException( "Unable to create log directory" );

			File linkFile = new File(fileDirectory, "links.csv");
			FileOutputStream fout = new FileOutputStream(linkFile, true);
			linkFileOut = new PrintWriter(fout);

			sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
			if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
				// success! we have an accelerometer
				accelerometer = sensorManager
						.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
				sensorManager.registerListener(this, accelerometer,
						SensorManager.SENSOR_DELAY_NORMAL);
				vibrateThreshold = accelerometer.getMaximumRange() / 2;

			} else {
				// fail we dont have an accelerometer!
			}
			v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

			myList.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						final int position, long id) {
					//final String destinationOfTalk = values.get(position).getName();
					final String destinationID = values.get(position).getID();
					Thread clickThread = new Thread() {
						public void run() {
							if(!isTalking) {
								if(values.get(position).getAvailability().equalsIgnoreCase("available")){
									setTalk(destinationID);

									// Tell the user that the message has been sent
									createToast("Talk broadcast!");
								}
								else {
									createToast("That person is busy. Try again later");
								}
							}
							else {
								createToast("You are already talking! Shake to end.");
							}
						}
					};
					clickThread.start();
				}
			});

			// Set the button's click listener
			joinButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {

					// Good practise to do I/O in a new thread
					Thread clickThread = new Thread() {
						public void run() {

							try {

								String chatMessage = myName.getText()
										.toString();
								lastStoredName = chatMessage;
								if (chatMessage != "") {
									// Construct the DTN message
									DtnMessage message = new DtnMessage();

									// Data part
									message.addData()
											// Create data chunk
											.writeInt(CREATE_NAME)
											.writeString(chatMessage); // Chat
																		// message

									// Broadcast the message using the fwd layer
									// interface
									fwdLayer.sendMessage(descriptor, message,
											"everyone", null);

									// Tell the user that the message has been
									// sent
									createToast("Chat message broadcast!");
								} else {
									createToast("Please enter name.");
								}
							} catch (Exception e) {
								// Log the exception
								Log.e("BroadcastApp",
										"Exception while sending message", e);
								// Inform the user
								createToast("Exception while sending message, check log");
							}
						}
					};
					clickThread.start();

					// Inform the user
					createToast("Broadcasting message...");
				}
			});

			// Start the middleware
			middleware = new DtnMiddlewareProxy(getApplicationContext());
			middleware.start(new MiddlewareListener() {
				public void onMiddlewareEvent(MiddlewareEvent event) {
					try {

						// Check if the middleware failed to start
						if (event.getEventType() != MiddlewareEvent.MIDDLEWARE_STARTED) {
							throw new Exception(
									"Middleware failed to start, is it installed?");
						}

						// Get the fwd layer API
						fwdLayer = new ForwardingLayerProxy(middleware);

						// Get a descriptor for this user
						// Typically, the user enters the username, but here we
						// simply use IMEI number
						TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
						myID = telephonyManager.getDeviceId();
						descriptor = fwdLayer.getDescriptor(
								"nus.dtn.app.broadcast",
								telephonyManager.getDeviceId());

						// Set the broadcast address
						fwdLayer.setBroadcastAddress("nus.dtn.app.broadcast",
								"everyone");

						// Register a listener for received chat messages
						ChatMessageListener messageListener = new ChatMessageListener();
						fwdLayer.addMessageListener(descriptor, messageListener);
					} catch (Exception e) {
						// Log the exception
						Log.e("BroadcastApp",
								"Exception in middleware start listener", e);
						// Inform the user
						createToast("Exception in middleware start listener, check log");
					}
				}
			});
		} catch (Exception e) {
			// Log the exception
			Log.e("BroadcastApp", "Exception in onCreate()", e);
			// Inform the user
			createToast("Exception in onCreate(), check log");
		}
	}

	public ServiceConnection conn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			DataCollectionService.MyBinder binder = (DataCollectionService.MyBinder) service;
			mPilocService = binder.getService();
			mPilocService.setFPConfAndStartColllectingFP(new FPConf(true,false));
			mPilocService.startCollection();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mPilocService.onDestroy();
		}
	};

	DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which){
				case DialogInterface.BUTTON_POSITIVE:
					//Yes button clicked
					for (ListModel person : values) {
						if(person.getID().equalsIgnoreCase(currentPersonTalkingTo)) {
							final StringBuilder sb = new StringBuilder();
							sb.append( person.getName() + "," );
							sb.append( person.getLink());
							linkFileOut.println( sb.toString() );
							linkFileOut.flush();
						}
					}
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					//No button clicked
					break;
			}
		}
	};

	/** Called when the activity is destroyed. */
	@Override
	protected void onDestroy() {
		super.onDestroy();

		try {
			linkFileOut.close();
			if(mPilocService!=null)
			{
				//Stop collecting annotated walking trajectories
				mPilocService.stopCollection();
			}
			//Unbind the service
			getApplicationContext().unbindService(conn);
			//Stop all localization threads
			mIsLocation = false;
			// Stop the middleware
			// Note: This automatically stops the API proxies, and releases
			// descriptors/listeners
			middleware.stop();
		} catch (Exception e) {
			// Log the exception

			Log.e("BroadcastApp", "Exception on stopping middleware", e);
			// Inform the user
			createToast("Exception while stopping middleware, check log");
		} finally {
			linkFileOut = null;
		}
	}

	/** Listener for received chat messages. */
	private class ChatMessageListener implements MessageListener {

		/** {@inheritDoc} */
		public void onMessageReceived(String source, String destination,
				DtnMessage message) {

			try {

				// Read the DTN message
				// Data part
				message.switchToData();
				int type = message.readInt();
				String chatMessage = message.readString();
				String valToWrite = "";
				
				if (lastStoredName != null) {

					if (!lastStoredName.equals(chatMessage)) {
						if (type == CREATE_NAME) {
							if (!values.contains(chatMessage)) {
								boolean check = false;
								if (map.containsKey(chatMessage)) {
									check = map.get(chatMessage);
								}
								if (!check) {
									ListModel temp = new ListModel();
									temp.setName(chatMessage);
									temp.setlastLocation(new String("1"));
									temp.setAvail(new String("Available"));
									temp.setID(source);
									values.add(temp);
									map.remove(chatMessage);
									map.put(chatMessage, false);
								}
							}
							broadcastSelf();
							// Append to the message list
							valToWrite = "Received from " + chatMessage + ","
									+ source;
						} else if (type == UPDATE_NAME) {
							if (map.containsKey(chatMessage)) {
								map.remove(chatMessage);
								map.put(chatMessage, false);
							} else {
								map.put(chatMessage, false);
								//values.add(chatMessage);
								ListModel temp = new ListModel();
								temp.setName(chatMessage);
								temp.setlastLocation(new String("1"));
								temp.setAvail(new String("Available"));
								temp.setID(source);
								values.add(temp);
							}

							valToWrite = "Received from " + chatMessage + ","
									+ source;
						} else if (type == TALK_NAME) {
							createToast("Got Request");
							//just to make sure it has in the list in case a broadcast didnt reach
							if (map.containsKey(chatMessage)) {
								map.remove(chatMessage);
								map.put(chatMessage, true);
							} else {
								map.put(chatMessage, true);
								//values.add(chatMessage);
								ListModel temp = new ListModel();
								temp.setName(chatMessage);
								temp.setlastLocation(new String("1"));
								temp.setAvail(new String("Available"));
								temp.setID(source);
								values.add(temp);
							}
							
							//respond to request
							String toreply="";
							
							try{
								DtnMessage reply = new DtnMessage();
								reply.addData() // Create data chunk
								.writeInt(CONFIRM_TALK).writeString(lastStoredName);
								
								
								for(int i=0;i<values.size();i++){
									//createToast(values.get(i).getName());
									if(values.get(i).getName().equals(chatMessage)){
										toreply = values.get(i).getID();
									}
								}
								
								fwdLayer.sendMessage(descriptor, reply, toreply, null);
								createToast("Sending reply");
								//IMPORTANT FOR RECEIVER
								isTalking = true;
								currentPersonTalkingTo = source;
								
							} catch (ForwardingLayerException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (DtnMessageException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
							
							valToWrite = "Request from " + chatMessage + ","
									+ source+",Replied to "+toreply;
						}
						else if(type == CONFIRM_TALK) {
							
							isTalking = true;
							createToast("Acknowledged");
							//update availability of those two and broadcast back in setTalk
							
							
						}
						else if(type == UPDATE_AVAIL){
							for(int i=0;i<values.size();i++){
								if(values.get(i).getName().equalsIgnoreCase(chatMessage)){
									if(values.get(i).getAvailability().equals("Available")){
									values.get(i).setAvail("Unavailable");
									}
									else{
										createToast(myID);
										if(values.get(i).getName().equalsIgnoreCase(chatMessage)) {
											builder.setMessage("Save profile?").setPositiveButton("Yes", dialogClickListener)
													.setNegativeButton("No", dialogClickListener).show();
										}
										values.get(i).setAvail("Available");	
									}
								}
							}
						}
						final String newText = valToWrite;
						// Update the text view in Main UI thread
						handler.post(new Runnable() {
							public void run() {
								textView_Message.setText(newText);
								adapter.notifyDataSetChanged();
							}
						});
					}
				} else {
					createToast("Please join first.");
				}

			} catch (Exception e) {
				// Log the exception
				Log.e("BroadcastApp", "Exception on message event", e);
				// Tell the user
				createToast("Exception on message event, check log");
			}
			
		}
		
	}

	private void broadcastSelf() {
		DtnMessage message = new DtnMessage();

		// Data part
		try {
			message.addData() // Create data chunk
					.writeInt(UPDATE_NAME).writeString(lastStoredName);

			fwdLayer.sendMessage(descriptor, message, "everyone", null);
		} catch (ForwardingLayerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DtnMessageException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	}

	private void setTalk(String destination) {
		DtnMessage message = new DtnMessage();
		DtnMessage message1 = new DtnMessage();
		DtnMessage message2 = new DtnMessage();
		// Data part
		try {
			message.addData() // Create data chunk
					.writeInt(TALK_NAME).writeString(lastStoredName);
			
			fwdLayer.sendMessage(descriptor, message, destination, null);
			
			//timeout and check isTalking
			//setupLongTimeout(50000);
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(isTalking){
				createToast("Now Talking with "+destination);
				currentPersonTalkingTo = destination;
				//here broadcast busy to everybody
				message1.addData().writeInt(UPDATE_AVAIL).writeString(lastStoredName);
				//BE CAREFUL HERE BECAUSE HAVE TO SEND NAME
				String availChange = "";
				for(int i=0;i<values.size();i++){
					if(values.get(i).getID().equals(destination)){
						availChange = values.get(i).getName();
					}
				}
				message2.addData().writeInt(UPDATE_AVAIL).writeString(availChange);
				fwdLayer.sendMessage(descriptor, message1, "everyone", null);
				fwdLayer.sendMessage(descriptor, message2, "everyone", null);
			}

			
			//fwdLayer.sendMessage(descriptor, message1, "everyone", null);
		} catch (ForwardingLayerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DtnMessageException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	private void stopTalking(){
		//isTalking is now false
		//broadcast available to everyone
		try {
		DtnMessage message1 = new DtnMessage();
		DtnMessage message2 = new DtnMessage();
			
		message1.addData().writeInt(UPDATE_AVAIL).writeString(lastStoredName);
		String unavailChange = "";
		for(int i=0;i<values.size();i++){
			if(values.get(i).getID().equals(currentPersonTalkingTo)){
				unavailChange = values.get(i).getName();
			}
		}
		message2.addData().writeInt(UPDATE_AVAIL).writeString(unavailChange);
		
		fwdLayer.sendMessage(descriptor, message1, "everyone", null);
		fwdLayer.sendMessage(descriptor, message2, "everyone", null);


		//builder.setMessage("Save profile?").setPositiveButton("Yes", dialogClickListener)
		//			.setNegativeButton("No", dialogClickListener).show();
		
		} catch (ForwardingLayerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DtnMessageException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	/** Helper method to create toasts. */
	private void createToast(String toastMessage) {

		// Use a 'final' local variable, otherwise the compiler will complain
		final String toastMessageFinal = toastMessage;

		// Post a runnable in the Main UI thread
		handler.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getApplicationContext(), toastMessageFinal,
						Toast.LENGTH_SHORT).show();
			}
		});
	}

	public void Localize(View v) {
		//if localizing already, do nothing
		if(mIsLocation) {
			return;
		}
		//check for radiomap
		if(mRadiomap==null) {
			new GetRadioMapTask().execute(null,null,null);
		}
		//start new thread to contniously update current location
		new Thread(
				new Runnable() {
					@Override
					public void run() {
						try {
							mIsLocation = true;
							while(mIsLocation) {
								//get current fingerprints
								Vector<Fingerprint> fp = mPilocService.getFingerprint();
								//find current location using fingerprints
								mCurrentLocation = mPilocService.getLocation(mRadiomap,fp);

								if(mCurrentLocation == null) {
									Thread.sleep(1000);
									continue;
								}
								else {
									if(mCurrentLocation != mPreviousLocation) {
										//updateLocation
									}
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
		);
	}

	private class GetRadioMapTask extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... s) {
			try {
				mRadiomap = mPilocService.getRadiomap(RADIOMAP_SERVER_IP,FLOOR_ID);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(String s) {
			if(mRadiomap==null) {
				createToast("Radiomap get failed");
			}
		}
	}

	/** Text View (displays messages). */
	private TextView textView_Message;
	/** Button to trigger action (sending message). */
	private Button joinButton;
	private ListView myList;
	private EditText myName;
	static private String lastStoredName;
	CustomAdapter adapter;
	ArrayList<ListModel> values;
	HashMap<String, Boolean> map;
	boolean isTalking;
	String currentPersonTalkingTo;
	boolean isWalking;

	private SensorManager sensorManager;
	private Sensor accelerometer;
	public Vibrator v;

	final int CREATE_NAME = 0;
	final int UPDATE_NAME = 1;
	final int TALK_NAME = 2;
	final int CONFIRM_TALK = 3;
	final int UPDATE_AVAIL = 4;
	
	final String FLOOR_ID = "4222";
	final String RADIOMAP_SERVER_IP = "piloc.d1.comp.nus.edu.sg";

	private float lastX, lastY, lastZ;
	private float deltaXMax = 0;

	private float deltaYMax = 0;

	private float deltaZMax = 0;

	public PrintWriter linkFileOut;

	private float deltaX = 0;

	private float deltaY = 0;

	private float deltaZ = 0;
	private float vibrateThreshold = 20;

	/** DTN Middleware API. */
	private DtnMiddlewareInterface middleware;
	/** Fwd layer API. */
	private ForwardingLayerInterface fwdLayer;

	/** Sender's descriptor. */
	private Descriptor descriptor;

	/** Handler to the main thread to do UI stuff. */
	private Handler handler;
	
	
	Timer longTimer;
	synchronized void setupLongTimeout(long timeout) {
	  if(longTimer != null) {
	    longTimer.cancel();
	    longTimer = null;
	  }
	  if(longTimer == null) {
	    longTimer = new Timer();
	    longTimer.schedule(new TimerTask() {
	      public void run() {
	        longTimer.cancel();
	        longTimer = null;
	        //do your stuff, i.e. finishing activity etc.
	      }
	    }, timeout /*delay in milliseconds i.e. 5 min = 300000 ms or use timeout argument*/);
	  }
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		// get the change of the x,y,z values of the accelerometer

		        deltaX = Math.abs(lastX - event.values[0]);

		        deltaY = Math.abs(lastY - event.values[1]);

		        deltaZ = Math.abs(lastZ - event.values[2]);
		        // if the change is below 2, it is just plain noise
		        if (deltaX < 2)
		            deltaX = 0;
		        if (deltaY < 2)
		            deltaY = 0;
		        if ((deltaX > vibrateThreshold) || (deltaY > vibrateThreshold) || (deltaZ > vibrateThreshold)) {
		            //v.vibrate(50);
		        	if(isWalking){
		        		isTalking = false;
		        		isWalking = false;
		        		stopTalking();
		        		createToast("Talking done.");
		        	}
		        	else if(isTalking){
		            	isWalking=true;
		            	createToast("Walking started.");
		            }
		        }

	}
}
