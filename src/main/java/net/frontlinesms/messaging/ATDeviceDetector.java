package net.frontlinesms.messaging;

import java.io.*;
import java.util.*;

import serial.*;

public class ATDeviceDetector extends Thread {
	/** Valid baud rates */
	private static final int[] BAUD_RATES = { 9600, 14400, 19200, 28800, 33600, 38400, 56000, 57600, 115200, 230400, 460800, 921600 };

	/** Logger */
	private final Logger log = new Logger(this.getClass());
	/** Port this is detecting on */
	private final CommPortIdentifier portIdentifier;
	/** The top speed the device was detected at. */
	private int maxBaudRate;
	/** The serial number of the detected device. */
	private String serial;
	/** <code>true</code> when the detection thread has finished. */
	private boolean finished;
	
	private String exceptionMessage;
	
	public ATDeviceDetector(CommPortIdentifier port) {
		super("ATDeviceDetector: " + port.getName());
		this.portIdentifier = port;
	}
	
	public void run() {
		for(int baud : BAUD_RATES) {
			SerialPort serialPort = null;
			InputStream in = null;
			OutputStream out = null;
			
			/* This detection workflow was taken from ComTest in SMSLib, and is licensed under Apache v2. */
			try {
				serialPort = portIdentifier.open("ATDeviceDetector", 2000);
				serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN);
				serialPort.setSerialPortParams(baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
				in = serialPort.getInputStream();
				out = serialPort.getOutputStream();
				serialPort.enableReceiveTimeout(1000);
				
				log.trace("LOOPING.");
				
				// discard all data currently waiting on the input stream
				Utils.readAll(in);
				Utils.writeCommand(out, "AT");
				Utils.sleep(1000);
				String response = Utils.readAll(in);
				if(!Utils.isResponseOk(response)) {
					throw new ATDeviceDetectionException("Bad response: " + response);
				} else {
					Utils.writeCommand(out, "AT+CGSN");
					response = Utils.readAll(in);
					if(!Utils.isResponseOk(response)) {
						throw new ATDeviceDetectionException("Bad response to request for serial number: " + response);
					} else {
						String serial = Utils.trimResponse("AT+CGSN", response);
						log.debug("Found serial: " + serial);
						if(this.serial != null) {
							// There was already a serial detected.  Check if it's the same as
							// what we've just got.
							if(!this.serial.equals(serial)) {
								log.info("New serial detected: '" + serial + "'.  Replacing previous: '" + this.serial + "'");
							}
						}
						this.serial = serial;
						maxBaudRate = Math.max(maxBaudRate, baud);
					}
				}
			} catch(Exception ex) {
				log.info("Problem connecting to device.", ex);
				this.exceptionMessage = ex.getMessage();
			} finally {
				// Close any open streams
				if(out != null) try { out.close(); } catch(Exception ex) { log.warn("Error closing output stream.", ex); }
				if(in != null) try { in.close(); } catch(Exception ex) { log.warn("Error closing input stream.", ex); }
				if(serialPort != null) try { serialPort.close(); } catch(Exception ex) { log.warn("Error closing serial port.", ex); }
			}
		}
		finished = true;
		log.info("Detection completed on port: " + this.portIdentifier.getName());
	}
	
//> ACCESSORS
	public boolean isFinished() {
		return finished;
	}
	
	public boolean isDetected() {
		return this.maxBaudRate > 0;
	}
	
	public CommPortIdentifier getPortIdentifier() {
		return portIdentifier;
	}
	
	public int getMaxBaudRate() {
		return maxBaudRate;
	}
	
	public String getSerial() {
		assert(isDetected()) : "Cannot get serial if no device was detected.";
		return serial;
	}
	
	public String getExceptionMessage() {
		assert(!isDetected()) : "Cannot get Throwable clause if device was detected successfully.";
		return exceptionMessage;
	}
}