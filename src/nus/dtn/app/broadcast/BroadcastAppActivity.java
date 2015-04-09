package nus.dtn.app.broadcast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import nus.cirl.piloc.DataCollectionService;
import nus.cirl.piloc.DataStruture.FPConf;
import nus.cirl.piloc.DataStruture.Fingerprint;
import nus.cirl.piloc.DataStruture.Radiomap;
import nus.cirl.piloc.DataStruture.StepInfo;
import nus.cirl.piloc.PiLocHelper;
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
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
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
	private DataCollectionService mPilocService = null;
	/** Called when the activity is first created. */
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {

			// Specify what GUI to use
			setContentView(R.layout.main);

			// Set a handler to the current UI thread
			handler = new Handler();

			// Get references to the GUI widgets
			textView_Message = (TextView) findViewById(R.id.TextView_Message);
			joinButton = (Button) findViewById(R.id.JoinButton);
			myList = (ListView) findViewById(R.id.myList);
			myName = (EditText) findViewById(R.id.myName);

			values = new ArrayList<String>();
			map = new HashMap<String, Boolean>();
			isTalking = false;

			adapter = new ArrayAdapter<String>(this,
					android.R.layout.simple_list_item_1, android.R.id.text1,
					values);
			myList.setAdapter(adapter);

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
						int position, long id) {
					final String destinationOfTalk = values.get(position);
					Thread clickThread = new Thread() {
						public void run() {
							DtnMessage message = new DtnMessage();

							// Data part
							isTalking = true;
							setTalk(destinationOfTalk);

							// Tell the user that the message has been sent
							createToast("Chat message broadcast!");
						}
					};
					clickThread.start();
					createToast("Clicked on " + values.get(position));
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

	/** Called when the activity is destroyed. */
	@Override
	protected void onDestroy() {
		super.onDestroy();

		try {
			// Stop the middleware
			// Note: This automatically stops the API proxies, and releases
			// descriptors/listeners
			middleware.stop();
		} catch (Exception e) {
			// Log the exception
			Log.e("BroadcastApp", "Exception on stopping middleware", e);
			// Inform the user
			createToast("Exception while stopping middleware, check log");
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
									values.add(chatMessage);
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
								values.add(chatMessage);
							}

							valToWrite = "Received from " + chatMessage + ","
									+ source;
						} else if (type == TALK_NAME) {
							if (map.containsKey(chatMessage)) {
								map.remove(chatMessage);
								map.put(chatMessage, true);
							} else {
								map.put(chatMessage, true);
								values.add(chatMessage);
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
		// Data part
		try {
			message.addData() // Create data chunk
					.writeInt(TALK_NAME).writeString(lastStoredName);

			message1.addData().writeInt(TALK_NAME).writeString(destination);

			fwdLayer.sendMessage(descriptor, message, "everyone", null);
			fwdLayer.sendMessage(descriptor, message1, "everyone", null);
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
								Vector<Fingerprint> fp;
								//find current location using fingerprints
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
		);
	}

	/** Text View (displays messages). */
	private TextView textView_Message;
	/** Button to trigger action (sending message). */
	private Button joinButton;
	private ListView myList;
	private EditText myName;
	static private String lastStoredName;
	ArrayAdapter<String> adapter;
	ArrayList<String> values;
	HashMap<String, Boolean> map;
	boolean isTalking;

	private SensorManager sensorManager;
	private Sensor accelerometer;
	public Vibrator v;

	final int CREATE_NAME = 0;
	final int UPDATE_NAME = 1;
	final int TALK_NAME = 2;

	private float lastX, lastY, lastZ;
	private float deltaXMax = 0;

	private float deltaYMax = 0;

	private float deltaZMax = 0;

	private float deltaX = 0;

	private float deltaY = 0;

	private float deltaZ = 0;
	private float vibrateThreshold = 0;

	/** DTN Middleware API. */
	private DtnMiddlewareInterface middleware;
	/** Fwd layer API. */
	private ForwardingLayerInterface fwdLayer;

	/** Sender's descriptor. */
	private Descriptor descriptor;

	/** Handler to the main thread to do UI stuff. */
	private Handler handler;

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
		            if(isTalking){
		            	isTalking=false;
		            	createToast("Talking is over.");
		            }
		        }

	}
}
