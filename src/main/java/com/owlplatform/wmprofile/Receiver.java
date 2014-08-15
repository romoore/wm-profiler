package com.owlplatform.wmprofile;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import com.owlplatform.worldmodel.client.ClientWorldConnection;
import com.owlplatform.worldmodel.client.StepResponse;
import com.owlplatform.worldmodel.client.WorldState;

public class Receiver {


	public static void main(String[] args) {
		final String host = args[0];
		final int cPort = Integer.parseInt(args[1]);
		

		// final SolverWorldConnection swc = new SolverWorldConnection();
		final ClientWorldConnection cwc = new ClientWorldConnection();
		final Map<String,Long> times = new TreeMap<String,Long>();
		final Map<String,Long> relTimes = new TreeMap<String,Long>();

		final Thread mainThread = Thread.currentThread();

		cwc.setHost(host);
		cwc.setPort(cPort);

		if (!cwc.connect(5000)) {
			System.err.println("Unable to connect to " + cwc);
			return;
		}

		final StepResponse resp = cwc.getStreamRequest("id_.*", 0, 0, "a.*");
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("Shutting down...");
				resp.cancel();
				try {
					mainThread.join();
				} catch (InterruptedException ie) {
					// Ignored?
				}
				System.out.println("Joined main thread, building statistics");
				StringBuilder sb = new StringBuilder();
				long lastNanoTime = 0l;
				for (Iterator<String> tIter = times.keySet().iterator(), rIter = relTimes.keySet()
						.iterator(); tIter.hasNext() && rIter.hasNext();) {
					final String tid = tIter.next();
					final String rid = rIter.next();
					final long nanoTime = relTimes.get(rid);
					final long realTime = times.get(tid);
					final long nanoDiff = lastNanoTime == 0 ? 0 : (nanoTime - lastNanoTime);
					sb.append(tid).append("\tT: ").append(realTime).append("\n").append(rid).append("\tR: ").append(nanoDiff).append("\n");
					lastNanoTime = nanoTime;
				}
				System.out.println(sb.toString());
			}
		});

		while (!resp.isComplete() && !resp.isError()) {
			final long ts = System.currentTimeMillis();
			final long nano = System.nanoTime();
			WorldState a;
			try {
				a = resp.next();
			} catch (Exception e) {
				System.out.println("Caught exception " + e);
				e.printStackTrace(System.out);
				break;
			}
			
			for(String id : a.getIdentifiers()){
				times.put(id,ts);
				relTimes.put(id,nano);
			}
		}

	}

	public static void printUsage() {
		System.err
				.println("<WM HOST> <CLIENT PORT> ");
	}

}