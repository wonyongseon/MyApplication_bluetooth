package com.example.a510.myapplication_bluetooth;
 
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity  {

	// Debugging
	private static final String TAG = "Main";
    private static final boolean D = true;
    // Key names received from the BluetoothService Handler
    public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";
	// Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
	// Intent request code
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
	private static final int REQUEST_ENABLE_BT = 3;
    // Local Bluetooth adapter    
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the BluetoothService
    private BluetoothService mBtService = null;
    private Handler mBTmonit_TimerHandler;
    private Runnable mBTmonit_Timer = null;
    // Name of the connected device
    private String mConnectedDeviceName = null;
    private String lastMAC = null;
    private String lastTryMAC = null;
	// Layout Views
    private TextView mStatus_view;
    
    //*************************************************************************
    // Arduino Interface
    //*************************************************************************
    private TextView mPushButtonCount; 
    private int pushCount = 0;
    //*************************************************************************    
    
    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if(D) Log.e(TAG, "+++ ON CREATE +++");
		// Set up the window layout
		setContentView(R.layout.main);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
        	Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }
    @Override
    public void onStart() {
        super.onStart();
		if(D) Log.e(TAG, "++ ON START ++");
        if (!mBluetoothAdapter.isEnabled()) {
        	if(D) Log.d(TAG, "Bluetooth ON Request");
        	Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (mBtService == null) setupService();
        }
        if(mBTmonit_Timer == null){
        	mBTmonit_Timer = new Runnable() {
    			@Override
    			public void run() {
    				if ((mBtService != null) &&
    					(mBtService.getState() == BluetoothService.STATE_LISTEN) && 
    					(lastMAC != "")) {
    					lastTryMAC = lastMAC; 
    					if(D) Log.d(TAG, "Automatic Try-->" + lastMAC);
    					BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(lastMAC);
    					mBtService.connect(device, true);
    					mBTmonit_TimerHandler.postDelayed(this, 30*1000);
    				}	
    				else mBTmonit_TimerHandler.postDelayed(this, 5*1000);
    			}
    		};
    		mBTmonit_TimerHandler = new Handler();
    		mBTmonit_TimerHandler.postDelayed(mBTmonit_Timer, 2*1000);
        }
    }
    @Override
    public void onRestart() {
        super.onRestart();
        if(D) Log.e(TAG, "++ ON RESTART ++");
    }
    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "++ ON RESUME ++");
        if (mBtService != null) {
              if (mBtService.getState() == BluetoothService.STATE_NONE) {
                mBtService.start();
            }
        }
    }
    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "++ ON PAUSE ++");
    }
    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "++ ON STOP ++");
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if(D) Log.e(TAG, "++ ON DESTROY ++");
        if (mBtService != null) mBtService.stop();
        if(mBTmonit_Timer != null) mBTmonit_TimerHandler.removeCallbacks(mBTmonit_Timer);
      }
    
    private void setupService() {	
    	if(D) Log.d(TAG, "setupChat()");
    	mStatus_view = (TextView) findViewById(R.id.status_view);
    	
    	//*************************************************************************
        // Arduino Interface
        //*************************************************************************
        mPushButtonCount = (TextView) findViewById(R.id.PushButtonCount);
        //*************************************************************************        
        
		if(mBtService == null) {
			mBtService = new BluetoothService(this, mHandler);
		}
		configParaLoad();
    }
    
    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
          if (mBtService.getState() != BluetoothService.STATE_CONNECTED) {
        	Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
          if (message.length() > 0) {
              byte[] send = message.getBytes();
            mBtService.write(send);

        }
    }
    //========================== Options Menu ==========================    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
        case R.id.secure_connect_scan:
              serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
            return true;
        }
        return false;
    }
    //========================== 'BluetoothService' ������ Message ==========================
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
            	case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                    	case BluetoothService.STATE_CONNECTED:
                            setStatus(getString(R.string.status_connected_to, mConnectedDeviceName));
                        	if(lastMAC != lastTryMAC){
                        		lastMAC = lastTryMAC;
                        		configParaSave();
                        	}
                    		break;
                    	case BluetoothService.STATE_CONNECTING:
                    		setStatus(getString(R.string.status_connecting));
                    		break;
                    	case BluetoothService.STATE_LISTEN:
                    	case BluetoothService.STATE_NONE:
                    		setStatus(getString(R.string.status_not_connected));
                    		break;
                    }
            		break;
            	case MESSAGE_WRITE:
                    break;
                case MESSAGE_READ:
                /*
                 * msg.arg1 = length
                */
                	synchronized (this) {
                		byte[] readBuf = (byte[]) msg.obj;
                    	//*************************************************************************
                        // Arduino Interface (아두이노에서 수신)
                        //*************************************************************************
                		if( readBuf[0] == '*' &&
                			readBuf[1] == 'P' &&
                			readBuf[2] == '1' &&
                			readBuf[3] == '1' ){
                			pushCount++;
                			mPushButtonCount.setText("" + pushCount);
                		}
                        //*************************************************************************                		
                	}
               		break;

                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
            	case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),Toast.LENGTH_LONG).show();
                    break;
            }		
		}
	};
    private final void setStatus(String status) {
        mStatus_view.setText(status);
    }
	//========================== Intent Request Return ==========================
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(D) Log.d(TAG, "onActivityResult " + requestCode + "," + resultCode);
        switch (requestCode) {
        	case REQUEST_CONNECT_DEVICE_SECURE:
            // When DeviceListActivity returns with a device to connect
        		if (resultCode == Activity.RESULT_OK) {
        			connectDevice(data, true);
        		}
        		break;
        	case REQUEST_CONNECT_DEVICE_INSECURE:
            // When DeviceListActivity returns with a device to connect
        		if (resultCode == Activity.RESULT_OK) {
        			connectDevice(data, false);
        		}
        		break;
        	case REQUEST_ENABLE_BT:
        	// When the request to enable Bluetooth returns
        		if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
        			setupService();
        		} else {
                    // User did not enable Bluetooth or an error occurred
        			if(D) Log.e(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
        		}
        		break;
        }
	}
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        lastTryMAC = address;
    	if(D) Log.d(TAG, "last try MAC-->" + lastTryMAC);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mBtService.connect(device, secure);
    }
	//========================== File Handler ==========================
    private boolean configParaSave(){
    	int i;
    	boolean success = false;
    	File file = new File(this.getFilesDir(), "configPara.cfg");
    	try {
    		DataOutputStream stream = new DataOutputStream(new FileOutputStream(file));
    		
    		int len = 0;
    		if(lastMAC != ""){
    			byte [] byteMAC = lastMAC.getBytes();
    			len = byteMAC.length;
    			if(len > 31) len = 31;
    			stream.writeByte((byte)len);	//string length
    			for(i=0;i<len;i++){				//string (max 31)
        			stream.writeByte(byteMAC[i]);
        		}
    		}
    		else stream.writeByte(0);

    		if(len < 31){
				for(i=0;i<(31 - len);i++){	
    			stream.writeByte(0);
				}
    		}
    		
    		stream.flush();
    		stream.close();
    		success = true;
    	} catch (IOException e) {
    		e.printStackTrace();
    		success = false;
    	}
    	if(D) Log.d(TAG, "configParaSave()-->" + success);
    	return success;
    }
    private boolean configParaLoad(){
    	boolean success = false;
    	int i;

    	File file = new File(this.getFilesDir(), "configPara.cfg");
    	if(file.exists()){
        	try {
        		DataInputStream stream = new DataInputStream(new FileInputStream(file));

        		byte[] byteMAC;
        		byteMAC = new byte[32];
        		for(i=0;i<32;i++){	
        			byteMAC[i] = stream.readByte();
        		}
        		if((byteMAC[0] == 0) || (byteMAC[0] > 31)) lastMAC = "";
        		else lastMAC = new String(byteMAC, 1, byteMAC[0]);
        		
        		stream.close();
        		success = true;
        	} catch (IOException e) {
        		e.printStackTrace();
        	}
    	}	
    	if(!success){
    		lastMAC = "";
    	}
    	if(D) Log.d(TAG, "configParaLoad()-->" + success);
    	if(D) Log.d(TAG, "last MAC-->" + lastMAC);
    	return success;
   	}

	//*************************************************************************
    // Arduino Interface (아두이노로 전송)
    //*************************************************************************
    public void LedON(View view){
    	sendMessage("*L11\r\n");
    }
    public void LedOFF(View view){
    	sendMessage("*L10\r\n");
    }
	//*************************************************************************    
}
