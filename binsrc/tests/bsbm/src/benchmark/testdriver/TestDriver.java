package benchmark.testdriver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import java.util.Locale;

import org.apache.log4j.xml.DOMConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import java.io.*;
import java.util.StringTokenizer;

public class TestDriver {
	protected QueryMix queryMix;//The Benchmark Querymix
	protected int warmups = TestDriverDefaultValues.warmups;//how many Query mixes are run for warm up
	protected AbstractParameterPool parameterPool;//Where to get the query parameters from
	private ServerConnection server;//only important for single threaded runs
	private String queryMixFN = null;//"qm.txt";
	private File queryDir = TestDriverDefaultValues.queryDir;//where to take the queries from
	protected int nrRuns = TestDriverDefaultValues.nrRuns;
	private long seed = TestDriverDefaultValues.seed;//For the random number generators
        protected String connUser = "d2r";
        protected String connPwd = "d2rbsbm";
	protected String connEndpoint = null;
	protected String defaultGraph = TestDriverDefaultValues.defaultGraph;
	private String resourceDir = TestDriverDefaultValues.resourceDir;//Where to take the Test Driver data from
	private String xmlResultFile = TestDriverDefaultValues.xmlResultFile;
	private static Logger logger = Logger.getLogger( TestDriver.class );
	protected boolean[] ignoreQueries;//Queries to ignore
	public static final byte DB_CONNECTION = 11;
	public static final byte WS_CONNECTION = 12;
	public static final byte DEFAULT_FAMILY = 21;
	public static final byte MYSQL_FAMILY = 22;
	public static final byte VIRTUOSO_FAMILY = 23;
	public static final byte SPARQL_LANG = 31;
	public static final byte SQL_LANG = 32;
	public byte connectionType = WS_CONNECTION;
	public byte queryLang = SPARQL_LANG;
	public byte querySyntax = DEFAULT_FAMILY;
	public boolean runParametrized = false;
	public boolean multithreading = false;
	protected int nrThreads;
        protected String driverClassName = "com.mysql.jdbc.Driver";
	
	public TestDriver(String[] args) {
		processProgramParameters(args);
		System.out.print("Reading Test Driver data...");
		System.out.flush();
		if (TestDriver.SQL_LANG == queryLang)
			parameterPool = new SQLParameterPool(new File(resourceDir),seed);
		else
			parameterPool = new LocalSPARQLParameterPool(new File(resourceDir),seed);
		System.out.println("done");
	
		if(connEndpoint!=null && !multithreading){
			if(connectionType == TestDriver.DB_CONNECTION)
				server = new SQLConnection(connEndpoint, driverClassName, connUser, connPwd);
			else
				server = new SPARQLConnection(connEndpoint);
		} else if(multithreading) {
			//do nothing
		}
		else {
			printUsageInfos();
			System.exit(-1);
		}
	}
	
	public void init() {
		Query[] queries = null;
		Integer[] queryRun = null;
		
		if(queryMixFN==null) {
			Integer[] temp = { 1, 2, 2, 3, 2, 2, 4, 2, 2, 5, 7, 7, 6, 7, 7, 8, 9, 9, 8, 9, 9, 10, 10, 11, 12};
//			Integer[] temp = { 1, 2, 2, 3, 2, 2, 4, 2, 2, 5, 7, 7, 6, 7, 7, 8, 9, 9, 8, 9, 9, 10, 10, 10, 10};
//			Integer[] temp = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
//			Integer[] temp = { 8 };

			queryRun = temp;
		}
		else {
			queryRun = getQueryMixInfo(queryMixFN);
		}
		
		Integer maxQueryNr = 0;
		for(int i=0;i<queryRun.length;i++) {
			if(queryRun[i] != null && queryRun[i] > maxQueryNr)
				maxQueryNr = queryRun[i];
		}

		queries = new Query[maxQueryNr];
		ignoreQueries = new boolean[maxQueryNr];
		
		for(int i=0;i<ignoreQueries.length;i++) {
			ignoreQueries[i] = false;
		}
		
		//WHICH QUERIES TO IGNORE
//		ignoreQueries[4] = true;
		
		for(int i=0;i<queries.length;i++) {
			queries[i] = null;
		}
		for(int i=0;i<queryRun.length;i++) {
			if(queryRun[i]!=null) {
				Integer qnr = queryRun[i];
				if(queries[qnr-1]==null) {
					File queryFile = new File(queryDir, "query" + qnr + ".txt");
					File queryDescFile = new File(queryDir, "query" + qnr + "desc.txt");
					queries[qnr-1] = new Query(queryFile, queryDescFile, queryLang, querySyntax, defaultGraph, runParametrized);
				}
			}
		}
		
		
		queryMix = new QueryMix(queries, queryRun);
	}
	
	private Integer[] getQueryMixInfo(String queryMixFilename) {
		File file = new File(queryMixFilename);
		try {
			BufferedReader qmReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			StringBuffer data = new StringBuffer();
			String line = null;
			while((line=qmReader.readLine())!=null) {
				data.append(line);
				data.append(" ");
			}
				
			StringTokenizer st = new StringTokenizer(data.toString());
			while(st.hasMoreTokens());
				System.out.println(st.nextToken());
		} catch(IOException e) {
			System.err.println("Error processing query mix file: " + queryMixFilename);
			System.exit(-1);
		}
		return new Integer[2];
	}
	
	public void run() {
		
		for(int nrRun=-warmups;nrRun<nrRuns;nrRun++) {
			long startTime = System.currentTimeMillis();
			queryMix.setRun(nrRun);
			while(queryMix.hasNext()) {
				Query next = queryMix.getNext();
				Object[] queryParameters = parameterPool.getParametersForQuery(next);
				next.setParameters(queryParameters);
				if(ignoreQueries[next.getNr()-1])
					queryMix.setCurrent(0, -1.0);
				else {
					server.executeQuery(next, next.getQueryType());
				}
			}
			System.out.println(nrRun + ": " + String.format(Locale.US, "%.2f", queryMix.getQueryMixRuntime()*1000)
					+ "ms, total: " + (System.currentTimeMillis()-startTime) + "ms");
			queryMix.finishRun();
		}
		logger.log(Level.ALL, printResults(true));
		try {
			FileWriter resultWriter = new FileWriter(xmlResultFile);
			resultWriter.append(printXMLResults(true));
			resultWriter.flush();
			resultWriter.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * run the test driver in multi-threaded mode
	 */
	public void runMT() {
		ClientManager manager = new ClientManager(parameterPool, this);
		
		manager.createClients();
		manager.startWarmup();
		manager.startRun();
		logger.log(Level.ALL, printResults(true));
		try {
			FileWriter resultWriter = new FileWriter(xmlResultFile);
			resultWriter.append(printXMLResults(true));
			resultWriter.flush();
			resultWriter.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Process the program parameters typed on the command line.
	 */
	private void processProgramParameters(String[] args) {
		int i=0;
			try {
			while(i<args.length) {
				if(args[i].equals("-runs")) {
					nrRuns = Integer.parseInt(args[i++ + 1]);
				}
				else if(args[i].equals("-idir")) {
					resourceDir = args[i++ + 1];
				}
				else if(args[i].equals("-qdir")) {
					queryDir = new File(args[i++ + 1]);
				}
				else if(args[i].equals("-w")) {
					warmups = Integer.parseInt(args[i++ + 1]);
				}
				else if(args[i].equals("-o")) {
					xmlResultFile = args[i++ + 1];
				}
				else if(args[i].equals("-dg")) {
					defaultGraph = args[i++ + 1];
				}
				else if(args[i].startsWith("-sql")) {
					queryLang = SQL_LANG;
				}
				else if(args[i].startsWith("-sparql")){
					queryLang = SPARQL_LANG;
				}
				else if(args[i].startsWith("-dbconnect")) {
					connectionType = DB_CONNECTION;
				}
				else if(args[i].startsWith("-webservice")) {
					connectionType = WS_CONNECTION;
				}
				else if(args[i].startsWith("-mysql")) {
					querySyntax = MYSQL_FAMILY;
				}
				else if(args[i].startsWith("-virtuoso")) {
					querySyntax = VIRTUOSO_FAMILY;
				}
				else if(args[i].startsWith("-param")) {
					runParametrized = true;
				}
				else if (args[i].startsWith("-mt"))
				{
					multithreading = true;
					nrThreads = Integer.parseInt(args[i++ + 1]);
				}
				else if (args[i].startsWith("-dbdriver")) {
					driverClassName = args[i++ + 1];
				}
				else if (args[i].startsWith("-connuser")) {
					connUser = args[i++ + 1];
				}
				else if (args[i].startsWith("-connpwd")) {
					connPwd = args[i++ + 1];
				}
				else if(args[i].startsWith("-seed")) {
					seed = Long.parseLong(args[i++ + 1]);
				}
				else if(!args[i].startsWith("-")) {
					connEndpoint = args[i];
				}
				else {
					System.err.println("Unknown parameter: " + args[i]);
					printUsageInfos();
					System.exit(-1);
				}
				
				i++;
			}							
			} catch(Exception e) {
				System.err.println("Invalid arguments\n");
				printUsageInfos();
				System.exit(-1);
			}
		if (null == connEndpoint) {
			System.err.println("Connection endpoint is not set\n");
			printUsageInfos();
			System.exit(-1);
		}
	}
	
	/*
	 * Get Result String
	 */
	public String printResults(boolean all) {
		StringBuffer sb = new StringBuffer(100);
		double singleMultiRatio = 0.0;
		
		sb.append("Scale factor: " + parameterPool.getScalefactor() + "\n");
		sb.append("Number of warmup runs: " + warmups + "\n");
		if(multithreading)
			sb.append("Number of clients: " + nrThreads + "\n");
		sb.append("Seed: " + seed + "\n");
		sb.append("Number of query mix runs (without warmups): " + queryMix.getQueryMixRuns() + " times\n");
		sb.append("min/max Querymix runtime: " + String.format(Locale.US, "%.4fs",queryMix.getMinQueryMixRuntime()) +
				  " / " + String.format(Locale.US, "%.4fs",queryMix.getMaxQueryMixRuntime()) + "\n");
		if(multithreading) {
			sb.append("Total runtime (sum): " + String.format(Locale.US, "%.3f",queryMix.getTotalRuntime()) + " seconds\n");
			sb.append("Total actual runtime: " + String.format(Locale.US, "%.3f",queryMix.getMultiThreadRuntime()) + " seconds\n");
			singleMultiRatio = queryMix.getTotalRuntime()/queryMix.getMultiThreadRuntime();
		}
		else
			sb.append("Total runtime: " + String.format(Locale.US, "%.3f",queryMix.getTotalRuntime()) + " seconds\n");
		if(multithreading)
			sb.append("QMpH: " + String.format(Locale.US, "%.2f",queryMix.getMultiThreadQmpH()) + " query mixes per hour\n");
		else
			sb.append("QMpH: " + String.format(Locale.US, "%.2f",queryMix.getQmph()) + " query mixes per hour\n");
		sb.append("CQET: " + String.format(Locale.US, "%.5f",queryMix.getCQET()) + " seconds average runtime of query mix\n");
		sb.append("CQET (geom.):           " + String.format(Locale.US, "%.5f",queryMix.getQueryMixGeometricMean()) + " seconds geometric mean runtime of query mix\n");
		
		if(all) {
			sb.append("\n");
			//Print per query statistics
			Query[] queries = queryMix.getQueries();
			double[] qmin = queryMix.getQmin();
			double[] qmax = queryMix.getQmax();
			double[] qavga = queryMix.getAqet();//Arithmetic mean
			double[] avgResults = queryMix.getAvgResults();
			double[] qavgg = queryMix.getGeoMean();
			int[] qTimeout = queryMix.getTimeoutsPerQuery();
			int[] minResults = queryMix.getMinResults();
			int[] maxResults = queryMix.getMaxResults();
			int[] nrq = queryMix.getRunsPerQuery();
			for(int i=0;i<qmin.length;i++) {
				if(queries[i]!=null) {
					sb.append("Metrics for Query:      " + (i+1) + "\n");
					sb.append("Count: " + nrq[i] + " times executed in whole run\n");
					sb.append("AQET: " + String.format(Locale.US, "%.6f",qavga[i]) + " seconds (arithmetic mean)\n");
					sb.append("AQET(geom.): " + String.format(Locale.US, "%.6f",qavgg[i]) + " seconds (geometric mean)\n");
					sb.append("Cumulative time in run  " + String.format(Locale.US, "%.6f",qavga[i]*nrq[i]) + " seconds\n");
					if(multithreading)
						sb.append("QPS: " + String.format(Locale.US, "%.2f",singleMultiRatio/qavga[i]) + " Queries per second\n");
					else
					sb.append("QPS: " + String.format(Locale.US, "%.2f",1/qavga[i]) + " Queries per second\n");
					sb.append("minQET/maxQET: " + String.format(Locale.US, "%.8fs",qmin[i]) + " / " + 
							String.format(Locale.US, "%.8fs",qmax[i]) + "\n");
					if(queries[i].getQueryType()==Query.SELECT_TYPE) {
					sb.append("Average result count: " + String.format(Locale.US, "%.2f",avgResults[i]) + "\n");
					sb.append("min/max result count: " + minResults[i] + " / " + maxResults[i] + "\n");
					} else {
						sb.append("Average result (Bytes): " + String.format(Locale.US, "%.2f",avgResults[i]) + "\n");
						sb.append("min/max result (Bytes): " + minResults[i] + " / " + maxResults[i] + "\n");
					}
					sb.append("Number of timeouts: " + qTimeout[i] + "\n\n");
				}
			}
		}
		
		return sb.toString();
	}
	
	/*
	 * Get XML Result String
	 */
	public String printXMLResults(boolean all) {
		StringBuffer sb = new StringBuffer(100);
		double singleMultiRatio = 0.0;
		
		sb.append("<?xml version=\"1.0\"?>");
		sb.append("<bsbm>\n");
		sb.append("  <querymix>\n");
		sb.append("     <scalefactor>" + parameterPool.getScalefactor() + "</scalefactor>\n");
		sb.append("     <warmups>" + warmups + "</warmups>\n");
		if(multithreading)
			sb.append("     <nrthreads>" + nrThreads + "</nrthreads>\n");
		sb.append("     <seed>" + seed + "</seed>\n");
		sb.append("     <querymixruns>" + queryMix.getQueryMixRuns() + "</querymixruns>\n");
		sb.append("     <minquerymixruntime>" + 
				  String.format(Locale.US, "%.4f",queryMix.getMinQueryMixRuntime()) + "</minquerymixruntime>\n");
		sb.append("     <maxquerymixruntime>" + 
				  String.format(Locale.US, "%.4f",queryMix.getMaxQueryMixRuntime()) + "</maxquerymixruntime>\n");
		if(multithreading) {
			sb.append("     <totalruntime>" + String.format(Locale.US, "%.3f",queryMix.getTotalRuntime()) + "</totalruntime>\n");
			sb.append("     <actualtotalruntime>" + String.format(Locale.US, "%.3f",queryMix.getMultiThreadRuntime()) + "</actualtotalruntime>\n");
			singleMultiRatio = queryMix.getTotalRuntime()/queryMix.getMultiThreadRuntime();
		}
		else
			sb.append("     <totalruntime>" + String.format(Locale.US, "%.3f",queryMix.getTotalRuntime()) + "</totalruntime>\n");
		if(multithreading)
			sb.append("     <qmph>" + String.format(Locale.US, "%.2f",queryMix.getMultiThreadQmpH()) + "</qmph>\n");
		else
			sb.append("     <qmph>" + String.format(Locale.US, "%.2f",queryMix.getQmph()) + "</qmph>\n");
		sb.append("     <cqet>" + String.format(Locale.US, "%.5f",queryMix.getCQET()) + "</cqet>\n");
		sb.append("     <cqetg>" + String.format(Locale.US, "%.5f",queryMix.getQueryMixGeometricMean()) + "</cqetg>\n");
		sb.append("  </querymix>\n");
		
		if(all) {
			sb.append("  <queries>\n");
			//Print per query statistics
			Query[] queries = queryMix.getQueries();
			double[] qmin = queryMix.getQmin();
			double[] qmax = queryMix.getQmax();
			double[] qavga = queryMix.getAqet();
			double[] avgResults = queryMix.getAvgResults();
			double[] qavgg = queryMix.getGeoMean();
			int[] qTimeout = queryMix.getTimeoutsPerQuery();
			int[] minResults = queryMix.getMinResults();
			int[] maxResults = queryMix.getMaxResults();
			int[] nrq = queryMix.getRunsPerQuery();
			for(int i=0;i<qmin.length;i++) {
				if(queries[i]!=null) {
					sb.append("    <query nr=\"" + (i+1) + "\">\n");
					sb.append("      <executecount>" + nrq[i] + "</executecount>\n");
					sb.append("      <aqet>" + String.format(Locale.US, "%.6f",qavga[i]) + "</aqet>\n");
					sb.append("      <aqetg>" + String.format(Locale.US, "%.6f",qavgg[i]) + "</aqetg>\n");
					sb.append("      <qps>" + String.format(Locale.US, "%.2f",singleMultiRatio/qavga[i]) + "</qps>\n");
					sb.append("      <minqet>" + String.format(Locale.US, "%.8f",qmin[i]) + "</minqet>\n");
					sb.append("      <maxqet>" + String.format(Locale.US, "%.8f",qmax[i]) + "</maxqet>\n");
					sb.append("      <avgresults>" + String.format(Locale.US, "%.2f",avgResults[i]) + "</avgresults>\n");
					sb.append("      <minresults>" + minResults[i] + "</minresults>\n");
					sb.append("      <maxresults>" + maxResults[i] + "</maxresults>\n");
					sb.append("      <timeoutcount>" + qTimeout[i] + "</timeoutcount>\n");
					sb.append("    </query>\n");
				}
				else {
					sb.append("    <query nr=\"" + (i+1) + "\">\n");
					sb.append("      <executecount>0</executecount>\n");
					sb.append("      <aqet>0.0</aqet>\n");
					sb.append("    </query>\n");
				}
			}
			sb.append("  </queries>\n");
		}
		sb.append("</bsbm>\n");
		return sb.toString();
	}
	
	/*
	 * print command line options
	 */
	private void printUsageInfos() {
		String output = "Usage: java benchmark.testdriver.TestDriver <options> SPARQL-Endpoint\n\n" +
						"SPARQL-Endpoint: The URL of the HTTP SPARQL Endpoint\n\n" +
						"Possible options are:\n" +
						"\t-runs <number of query mix runs>\n" +
						"\t\tdefault: " + TestDriverDefaultValues.nrRuns + "\n" +
						"\t-idir <data input directory>\n" +
						"\t\tThe input directory for the Test Driver data\n" +
						"\t\tdefault: " + TestDriverDefaultValues.resourceDir + "\n" +
						"\t-qdir <query directory>\n" +
						"\t\tThe directory containing the query data\n" +
						"\t\tdefault: " + TestDriverDefaultValues.queryDir.getName() + "\n" +
						"\t-w <number of warm up runs before actual measuring>\n" +
						"\t\tdefault: " + TestDriverDefaultValues.warmups + "\n"+
						"\t-o <benchmark results output file>\n" +
						"\t\tdefault: " + TestDriverDefaultValues.xmlResultFile + "\n" +
						"\t-dg <Default Graph>\n" +
						"\t\tdefault: " + TestDriverDefaultValues.defaultGraph + "\n" +
						"\t-sql\n" +
						"\t\tuse JDBC connection to a RDB, default: not set\n" +
						"\t-mt <Number of clients>\n" +
						"\t\tRun multiple clients concurrently.\n" +
						"\t\tdefault: not set\n" +
						"\t-seed <Long Integer>\n" +
						"\t\tInit the Test Driver with another seed than the default.\n" +
						"\t\tdefault: " + TestDriverDefaultValues.seed + "\n";
		
		System.out.print(output);
	}
	
	public static void main(String argv[]) {
		DOMConfigurator.configureAndWatch( "log4j.xml", 60*1000 );
		TestDriver testDriver = new TestDriver(argv);
		testDriver.init();
		System.out.println("\nStarting test...\n");
		if(testDriver.multithreading)
			testDriver.runMT();
		else
			testDriver.run();
		System.out.println("\n" + testDriver.printResults(true));
	}
}
