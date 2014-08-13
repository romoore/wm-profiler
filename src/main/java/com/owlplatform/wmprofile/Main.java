package com.owlplatform.wmprofile;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;

import com.owlplatform.worldmodel.Attribute;
import com.owlplatform.worldmodel.client.ClientWorldConnection;
import com.owlplatform.worldmodel.client.Response;
import com.owlplatform.worldmodel.client.StepResponse;
import com.owlplatform.worldmodel.client.WorldState;
import com.owlplatform.worldmodel.solver.SolverWorldConnection;
import com.owlplatform.worldmodel.solver.protocol.messages.AttributeAnnounceMessage.AttributeSpecification;
import com.owlplatform.worldmodel.types.IntegerConverter;

public class Main {

	private static final Random RAND = new Random(System.currentTimeMillis());
	private static final String ORIGIN_STRING = "PROFILER";
	private static final String ATTRIBUTE_FORMAT = "a%05d";
	private static final String COUNT_FORMAT = "%,11d";
	private static final String TIME_FORMAT = "%,15d";

	public static void main(String[] args) {
		final String host = args[0];
		final int sPort = Integer.parseInt(args[1]);
		final int cPort = Integer.parseInt(args[2]);

		int numAttributes = 0;
		int numAttrPerId = 1;

		for (int i = 3; i < args.length; ++i) {
			if ("--createAttributes".equalsIgnoreCase(args[i])) {
				if ((i + 1) >= args.length) {
					System.err
							.println("Missing argument for \"createAttributes\" argument.");
					printUsage();
					return;
				}
				numAttributes = Integer.parseInt(args[++i]);
			}
		}
		final SolverWorldConnection swc = new SolverWorldConnection();
		final ClientWorldConnection cwc = new ClientWorldConnection();
		try {

			swc.setOriginString(ORIGIN_STRING);
			for (int i = 0; i < numAttrPerId; ++i) {
				final AttributeSpecification as = new AttributeSpecification();
				as.setAttributeName(String.format(ATTRIBUTE_FORMAT, i));
				as.setIsOnDemand(false);
				swc.addAttribute(as);
			}

			swc.setHost(host);
			swc.setPort(sPort);
			if (!swc.connect(5000)) {
				System.err.println("Unable to connect to " + swc);
				return;
			}

			cwc.setHost(host);
			cwc.setPort(cPort);

			if (!cwc.connect(5000)) {
				System.err.println("Unable to connect to " + cwc);
				return;
			}

			if (numAttributes > 0) {

				final Calendar cal = Calendar.getInstance();
				final long latestTime = cal.getTimeInMillis();
				cal.add(Calendar.MINUTE, -(numAttributes * numAttrPerId));
				final long earliestTime = cal.getTimeInMillis();

				final ArrayList<Attribute> attrs = genAttributes(numAttributes,
						1, earliestTime);

				final long startCreateAll = System.nanoTime();
				swc.updateAttributes(attrs);
				final long endCreateAll = System.nanoTime();
				for (final Attribute a : attrs) {
					swc.delete(a.getId(), a.getAttributeName());
				}
				final long endDeleteInd = System.nanoTime();
				for (int i = 0; i < numAttributes; ++i) {
					swc.updateAttribute(attrs.get(i));
				}
				final long endCreateInd = System.nanoTime();

				System.out.println(String.format("CA(" + COUNT_FORMAT + "): "
						+ TIME_FORMAT + "\nDI(" + COUNT_FORMAT + "): "
						+ TIME_FORMAT + "\nCI(" + COUNT_FORMAT + "): "
						+ TIME_FORMAT, numAttributes,
						(endCreateAll - startCreateAll), numAttributes,
						(endDeleteInd - endCreateAll), numAttributes,
						(endCreateInd - endDeleteInd)));

				System.out.println("Sleeping 5 seconds");
				try {
					Thread.sleep(5000);
				} catch (InterruptedException ie) {
					// Ignored
				}

				// Select entire range
				final long startEntireRange = System.nanoTime();
				StepResponse resp = cwc.getRangeRequest(".*", earliestTime,
						latestTime, ".*");
				while (!resp.isComplete()) {
					Thread.yield();
				}
				final long endEntireRange = System.nanoTime();
				System.out.println(String.format("RA: "
						+ TIME_FORMAT, (endEntireRange - startEntireRange)));
				
				long startTenPct = earliestTime;
				// Now select 10% at a time
				for(int i = 1; i < 11	; ++i){
					final long endTenPct = (long)((latestTime-earliestTime)*(i/10.0)) + earliestTime;
					final long start = System.nanoTime();
					resp = cwc.getRangeRequest(".*", startTenPct, endTenPct, ".*");
					while(!resp.isComplete()){
						Thread.yield();
					}
					final long end = System.nanoTime();
					
					int count = 0;
					while(resp.hasNext()){
						try {
							resp.next();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						++count;
					}
					
					System.out.println(String.format("R10/%2d("+COUNT_FORMAT+": "
							+ TIME_FORMAT, i,count,(end - start)));
					
					startTenPct = endTenPct;
				}
				
				System.out.println("Sleeping 5 seconds");
				try {
					Thread.sleep(5000);
				} catch (InterruptedException ie) {
					// Ignored
				}

				// Now select 5% at a time
				long start05Pct = earliestTime;
				// Now select 10% at a time
				for(int i = 1; i < 21; ++i){
					final long end20Pct = (long)((latestTime-earliestTime)*(i/20.0)) + earliestTime;
					final long start = System.nanoTime();
					resp = cwc.getRangeRequest(".*", start05Pct, end20Pct, ".*");
					while(!resp.isComplete()){
						Thread.yield();
					}
					final long end = System.nanoTime();
					int count = 0;
					while(resp.hasNext()){
						try {
							resp.next();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						++count;
					}
					System.out.println(String.format("R20/%2d("+COUNT_FORMAT+"): "
							+ TIME_FORMAT, i,count,(end - start)));
					
					start05Pct = end20Pct;
					
				}
				
				// Now snapshots at 5% steps, starting from 2.5% -> 97.5% 
				System.out.println("Sleeping 5 seconds");
				try {
					Thread.sleep(5000);
				} catch (InterruptedException ie) {
					// Ignored
				}

				for(int i = 0; i < 20; ++i){
					final long end05Snap = (long)((latestTime-earliestTime)*(2.5 + i/20.0)) + earliestTime;
					final long start = System.nanoTime();
					WorldState state = null;
					try {
						state = cwc.getSnapshot(".*", end05Snap, end05Snap, ".*").get();
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					final long end = System.nanoTime();
					int count = 0;
					if(null != state){
						count = state.getIdentifiers().size();
					}
					
					System.out.println(String.format("S20/%2.1f(" + COUNT_FORMAT + "): "
							+ TIME_FORMAT, 2.5+5*i,count,(end - start)));
					
				}

			}
		} finally {
			cwc.disconnect();
			swc.disconnect();
		}

	}

	public static void printUsage() {
		System.err
				.println("<WM HOST> <SOLVER PORT> <CLIENT PORT> [--createAttributes <NUM ATTRS>] [--time <TIMESTAMP1> <TIMESTAMP2>]");
	}

	public static ArrayList<Attribute> genAttributes(int idCount,
			int attrPerId, final long startTime) {
		ArrayList<Attribute> attrs = new ArrayList<Attribute>(idCount
				* attrPerId);
		final int numMinutes = attrs.size();

		final Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(startTime);

		for (int i = 0; i < idCount; i += attrPerId) {
			for (int j = 0; j < attrPerId; ++j) {
				final Attribute a = new Attribute();
				attrs.add(a);
				a.setId(String.format("id_%09d", i));
				a.setAttributeName(String.format(ATTRIBUTE_FORMAT, j));
				a.setData(IntegerConverter.get().encode(
						Integer.valueOf(RAND.nextInt())));

				a.setOriginName(ORIGIN_STRING);
				a.setCreationDate(cal.getTimeInMillis());
				cal.add(Calendar.MINUTE, 1);
			}
		}

		return attrs;
	}
}