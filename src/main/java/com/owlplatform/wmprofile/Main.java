package com.owlplatform.wmprofile;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
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
	private static final int SEARCH_ITERATIONS = 10;

	private static final String REGEX_PATTERN_SIMPLE = ".*";
	private static final String REGEX_PATTERN_MEDIUM = "^(.*?[a-z])(.*[0-9]){8}9";
	private static final String REGEX_PATTERN_COMPLEX = "(([^\\.]*))*1";
	private static final String REGEX_PATTERN_COMPLEX_HI_EMPTY = "(([^\\.]*))*p";

	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		final String host = args[0];
		final int sPort = Integer.parseInt(args[1]);
		final int cPort = Integer.parseInt(args[2]);

		int numAttributes = 0;
		int numAttrPerId = 1;
		boolean expire = false;
		boolean individual = false;

		for (int i = 3; i < args.length; ++i) {
			if ("--createAttributes".equalsIgnoreCase(args[i])) {
				if ((i + 1) >= args.length) {
					System.err
							.println("Missing argument for \"createAttributes\" argument.");
					printUsage();
					return;
				}
				numAttributes = Integer.parseInt(args[++i]);
			} else if ("--expire".equalsIgnoreCase(args[i])) {
				expire = true;
				System.out.println("Expiring attributes.");
			} else if ("--individual".equalsIgnoreCase(args[i])) {
				individual = true;
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
						1, earliestTime, expire);

				if (individual) {
					final long startCreateInd = System.nanoTime();
					for (int i = 0; i < numAttributes; ++i) {
						swc.updateAttribute(attrs.get(i));
					}
					final long endCreateInd = System.nanoTime();

					System.out.println(String.format("CI(" + COUNT_FORMAT
							+ "): " + TIME_FORMAT, numAttributes,
							(endCreateInd - startCreateInd)));
					return;
				}

				// Break-up into messages of 1M or fewer
				final int MAX_ATTR_MESSAGE = 1000000;
				if (numAttributes > MAX_ATTR_MESSAGE) {
					int numMessages = (int) Math.ceil(numAttributes
							/ (float) MAX_ATTR_MESSAGE);
					int lastArray = numAttributes % MAX_ATTR_MESSAGE;

					ArrayList[] attrArr = new ArrayList[numMessages];
					for (int i = 0; i < numMessages - 1; ++i) {
						attrArr[i] = new ArrayList<Attribute>();
						((ArrayList<Attribute>) attrArr[i]).addAll(attrs
								.subList(i * MAX_ATTR_MESSAGE, (i + 1)
										* MAX_ATTR_MESSAGE));
					}
					attrArr[numMessages - 1] = new ArrayList<Attribute>();
					((ArrayList<Attribute>) attrArr[numMessages - 1])
							.addAll(attrs.subList((numMessages - 1)
									* MAX_ATTR_MESSAGE,
									((numMessages - 1) * MAX_ATTR_MESSAGE)
											+ lastArray));

					final long startCreateAll = System.nanoTime();
					for (int i = 0; i < attrArr.length; ++i) {
						swc.updateAttributes(attrArr[i]);
					}
					final long endCreateAll = System.nanoTime();

					System.out.println(String.format("CA(" + COUNT_FORMAT
							+ "): " + TIME_FORMAT, numAttributes,
							(endCreateAll - startCreateAll)));
				} else {

					final long startCreateAll = System.nanoTime();
					swc.updateAttributes(attrs);
					final long endCreateAll = System.nanoTime();

					System.out.println(String.format("CA(" + COUNT_FORMAT
							+ "): " + TIME_FORMAT, numAttributes,
							(endCreateAll - startCreateAll)));
				}
				if (expire) {
					System.out.println("Expiring attributes");
					for (Attribute a : attrs) {
						swc.expire(a.getId(), a.getExpirationDate(),
								a.getAttributeName());
					}
				}

				System.out.println("Sleeping 30 seconds");
				try {
					Thread.sleep(30000);
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
				System.out.println(String.format("RA: " + TIME_FORMAT,
						(endEntireRange - startEntireRange)));

				long startTenPct = earliestTime;
				// Now select 10% at a time
				for (int i = 1; i < 11; ++i) {
					final long endTenPct = (long) ((latestTime - earliestTime) * (i / 10.0))
							+ earliestTime;
					final long start = System.nanoTime();
					resp = cwc.getRangeRequest(".*", startTenPct, endTenPct,
							".*");
					while (!resp.isComplete()) {
						Thread.yield();
					}
					final long end = System.nanoTime();

					int count = 0;
					while (resp.hasNext()) {
						try {
							resp.next();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						++count;
					}

					System.out.println(String.format("R10/%2d(" + COUNT_FORMAT
							+ "): " + TIME_FORMAT, i, count, (end - start)));

					startTenPct = endTenPct;
				}

				System.out.println("Sleeping 1 second");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					// Ignored
				}

				// Now select 5% at a time
				long start05Pct = earliestTime;

				for (int i = 1; i < 21; ++i) {
					final long end05Pct = (long) ((latestTime - earliestTime) * (i / 20.0))
							+ earliestTime;
					final long start = System.nanoTime();
					resp = cwc
							.getRangeRequest(".*", start05Pct, end05Pct, ".*");
					while (!resp.isComplete()) {
						Thread.yield();
					}
					final long end = System.nanoTime();
					int count = 0;
					while (resp.hasNext()) {
						try {
							resp.next();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						++count;
					}
					System.out.println(String.format("R05/%2d(" + COUNT_FORMAT
							+ "): " + TIME_FORMAT, i, count, (end - start)));

					start05Pct = end05Pct;

				}

				System.out.println("Sleeping 1 second");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					// Ignored
				}

				// Now select 1% at a time
				long start01Pct = earliestTime;

				for (int i = 1; i < 101; ++i) {
					final long end01Pct = (long) ((latestTime - earliestTime) * (i / 100.0))
							+ earliestTime;
					final long start = System.nanoTime();
					resp = cwc
							.getRangeRequest(".*", start01Pct, end01Pct, ".*");
					while (!resp.isComplete()) {
						Thread.yield();
					}
					final long end = System.nanoTime();
					int count = 0;
					while (resp.hasNext()) {
						try {
							resp.next();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						++count;
					}
					System.out.println(String.format("R01/%2d(" + COUNT_FORMAT
							+ "): " + TIME_FORMAT, i, count, (end - start)));

					start01Pct = end01Pct;

				}

				// Now snapshots at 5% steps, starting from 2.5% -> 97.5%
				System.out.println("Sleeping 1 second");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					// Ignored
				}

				for (int i = 0; i < 20; ++i) {
					final long end05Snap = (long) ((latestTime - earliestTime) * (.025 + (i / 20.0)))
							+ earliestTime;
					final long start = System.nanoTime();
					WorldState state = null;
					try {
						state = cwc.getSnapshot(".*", end05Snap, end05Snap,
								"a.*").get();
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					final long end = System.nanoTime();
					int count = 0;
					if (null != state) {
						for (String id : state.getIdentifiers()) {
							Collection<Attribute> as = state.getState(id);
							count += as.size();
						}
					}

					System.out.println(String.format("S/%2.1f(" + COUNT_FORMAT
							+ "): " + TIME_FORMAT, 2.5 + 5 * i, count,
							(end - start)));

				}

				// Now URI search
				System.out.println("Sleeping 1 second");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					// Ignored
				}

				for (int i = 0; i < SEARCH_ITERATIONS; ++i) {
					final long start = System.nanoTime();
					String[] ids = cwc.searchId(REGEX_PATTERN_SIMPLE);
					final long end = System.nanoTime();

					System.out.println(String.format("Rx/S(" + COUNT_FORMAT
							+ "): " + TIME_FORMAT, ids.length, (end - start)));
				}

				System.out.println("Sleeping 1 seconds");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					// Ignored
				}

				for (int i = 0; i < SEARCH_ITERATIONS; ++i) {
					final long start = System.nanoTime();
					String[] ids = cwc.searchId(REGEX_PATTERN_MEDIUM);
					final long end = System.nanoTime();

					System.out.println(String.format("Rx/M(" + COUNT_FORMAT
							+ "): " + TIME_FORMAT, ids.length, (end - start)));
				}

				System.out.println("Sleeping 1 seconds");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					// Ignored
				}

				for (int i = 0; i < SEARCH_ITERATIONS; ++i) {
					final long start = System.nanoTime();
					String[] ids = cwc.searchId(REGEX_PATTERN_COMPLEX);
					final long end = System.nanoTime();

					System.out.println(String.format("Rx/C(" + COUNT_FORMAT
							+ "): " + TIME_FORMAT, ids.length, (end - start)));
				}

				System.out.println("Sleeping 1 seconds");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					// Ignored
				}

				for (int i = 0; i < SEARCH_ITERATIONS; ++i) {
					final long start = System.nanoTime();
					String[] ids = cwc.searchId(REGEX_PATTERN_COMPLEX_HI_EMPTY);
					int count = 0;
					if (ids != null) {
						count = ids.length;
					}

					final long end = System.nanoTime();

					System.out.println(String.format("Rx/C_(" + COUNT_FORMAT
							+ "): " + TIME_FORMAT, count, (end - start)));
				}

				System.out.println("Sleeping 1 seconds");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					// Ignored
				}

				final long startDeleteInd = System.nanoTime();
				for (final Attribute a : attrs) {
					swc.delete(a.getId(), a.getAttributeName());
				}
				final long endDeleteInd = System.nanoTime();
				System.out.println(String.format("DI(" + COUNT_FORMAT + "): "
						+ TIME_FORMAT, numAttributes,
						(endDeleteInd - startDeleteInd)));
				return;

			}
		} finally {
			cwc.disconnect();
			swc.disconnect();
		}

	}

	public static void printUsage() {
		System.err
				.println("<WM HOST> <SOLVER PORT> <CLIENT PORT> [--createAttributes <NUM ATTRS>] [--expire]");
	}

	public static ArrayList<Attribute> genAttributes(int idCount,
			int attrPerId, final long startTime, final boolean expireEach) {
		ArrayList<Attribute> attrs = new ArrayList<Attribute>(idCount
				* attrPerId);
		final int numMinutes = attrs.size();

		final Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(startTime);

		for (int i = 0; i < idCount; i += attrPerId) {
			for (int j = 0; j < attrPerId; ++j) {
				final Attribute a = new Attribute();
				attrs.add(a);
				a.setId(String.format("id_%030d", i));
				a.setAttributeName(String.format(ATTRIBUTE_FORMAT, j));
				a.setData(IntegerConverter.get().encode(
						Integer.valueOf(RAND.nextInt())));

				a.setOriginName(ORIGIN_STRING);
				a.setCreationDate(cal.getTimeInMillis());
				cal.add(Calendar.MINUTE, 1);
				if (expireEach) {
					a.setExpirationDate(cal.getTimeInMillis());
				}
			}
		}

		return attrs;
	}
}