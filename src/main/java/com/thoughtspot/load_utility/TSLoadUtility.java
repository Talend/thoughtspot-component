package com.thoughtspot.load_utility;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TSLoadUtility {
	private String host;
	private int port;
	private String username;
	private String password;
	private Session session;
	private String command;
	private static final transient Logger LOG = LoggerFactory.getLogger(TSLoadUtility.class);
	//private static TSLoadUtility instance = null;
	
	public static synchronized TSLoadUtility getInstance(String host, int port, String username, String password)
	{
		//if (instance == null)
			return new TSLoadUtility(host, port, username, password);

		//return instance;
	}
	
	private TSLoadUtility(String host, int port, String username, String password)
	{
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
	}
	
	private TSLoadUtility(String host, String username, String password)
	{
		this(host, 22, username, password);
	}
	
	public void connect() throws TSLoadUtilityException {
		try {
			java.util.Properties config = new java.util.Properties(); 
	    	config.put("StrictHostKeyChecking", "no");
	    	JSch jsch = new JSch();
	    	Session session=jsch.getSession(this.username, this.host, this.port);
	    	session.setPassword(password);
	    	session.setConfig(config);
	    	
	    	session.connect();
	    	this.session = session;
		} catch(JSchException e) {
			throw new TSLoadUtilityException(e.getMessage(), e);
		}
	}
	
	public void setTSLoadProperties(String database, String schema, String table, int maxIgnoreRows, 
	String badRecordsFile, String date_format, String date_time_format, String time_format, int verbosity,
	boolean skip_second_fraction, String field_separator, boolean date_converted_to_epoch, String boolean_representation,
	String escape_character, String enclosing_character) {
		StringBuilder sb = new StringBuilder();
		sb.append("gzip -dc | tsload --target_database ");
		sb.append(database);
		sb.append(" --target_schema ");
		sb.append(schema);
		sb.append(" --target_table ");
		sb.append(table);
		sb.append(" --max_ignored_rows ");
		sb.append(maxIgnoreRows);
		if (badRecordsFile != null && badRecordsFile.trim().length() > 0) {
			sb.append(" --bad_records_file ");
			sb.append(badRecordsFile);
		}
		sb.append(" --date_format ");
		sb.append(date_format);
		sb.append(" --date_time_format ");
		sb.append("\""+date_time_format+"\"");
		sb.append(" --time_format ");
		sb.append(time_format);
		sb.append(" --v=" + verbosity);
		if (skip_second_fraction)
			sb.append(" --skip_second_fraction ");
		sb.append(" --field_separator ");
		sb.append("'"+field_separator+"'");
		sb.append(" --date_converted_to_epoch ");
		sb.append(date_converted_to_epoch);
		sb.append(" --boolean_representation ");
		sb.append(boolean_representation);
		sb.append(" --null_value ''");
		if (escape_character.trim().length() > 0) {
			sb.append(" --escape_character ");
			sb.append("\""+escape_character+"\"");
		}
		if (enclosing_character.trim().length() > 0) {
			sb.append(" --enclosing_character ");
			sb.append("\"\\"+enclosing_character+"\"");
		}
		this.command = sb.toString();
	}
	
	public LinkedHashMap<String,String> getTableColumns(String database, String schema, String table) throws TSLoadUtilityException {
		LinkedHashMap<String, String> columns = new LinkedHashMap<String, String>();
		try {
Channel channel=session.openChannel("shell");
			
			PipedOutputStream pos = new PipedOutputStream();
	        PipedInputStream pis = new PipedInputStream(pos);
	        channel.setInputStream(pis);
	        pos.write(("tql\nuse "+database+";\nshow table "+schema+"."+table+";\nexit;\nexit\n").getBytes());
	        pos.flush();
	        pos.close();
	        InputStream in=channel.getInputStream();
	        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        channel.setOutputStream(baos);
	        channel.connect();
	        Thread.sleep(10000);
	        
	        String[] output = new String(baos.toByteArray()).replaceAll("\r", "").split("\n");
	        boolean flag = false;
	        
	        for (String line : output)
	        {
	        	if (flag && line.startsWith("Statement"))
	        	{
	        		flag = false;
	        	}
	        	if (flag && !line.startsWith("-"))
	        	{
	        		String[] segments = line.split("\\|");
	        		if (segments.length == 5)
	        			// Checking if Date, DateTime, Time, Timestamp column types
	        			columns.put(segments[0].trim(),segments[4].trim());
	        		else
						columns.put(segments[0].trim(),segments[2].trim());
	        	}
	        	if (line.contains("|"))
	        	{
	        		flag = true;
	        	}
	        	
	        }

	        channel.disconnect();
			return columns;
		} catch(JSchException | IOException | InterruptedException e)
		{
			throw new TSLoadUtilityException(e.getMessage(), e);
		}
	}

	public void truncateTable(String database, String schema, String table) throws TSLoadUtilityException
	{
		StringBuilder command = new StringBuilder();
		command.append("tql\nuse " + database+";\ntruncate table " + schema+"."+table +";\nexit;\nexit\n");
		LOG.info("TSLU:: " + command.toString());
		try {
			Channel channel=session.openChannel("shell");

			PipedOutputStream pos = new PipedOutputStream();
			PipedInputStream pis = new PipedInputStream(pos,131072);
			channel.setInputStream(pis);
			pos.write(command.toString().getBytes());
			pos.flush();
			pos.close();
			InputStream in=channel.getInputStream();
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			channel.setOutputStream(baos);
			channel.connect();

			Thread.sleep(10000);
			String output = new String(baos.toByteArray()).replaceAll("\r", "");
			LOG.info("TSLU:: " + output);
			if (!output.contains("Statement executed successfully."))
				throw new TSLoadUtilityException(output, new Exception(output));
			channel.disconnect();
		} catch(JSchException | IOException | InterruptedException e) {
			LOG.error("TSLU:: " + e.getMessage());
			throw new TSLoadUtilityException(e.getMessage(),e);
		}
	}
	public void createDatabase(String database) throws TSLoadUtilityException
	{
		StringBuilder command = new StringBuilder();
		command.append("tql\ncreate database " + database+";\nexit;\nexit\n");
		LOG.info("TSLU:: " + command.toString());
		try {
			Channel channel=session.openChannel("shell");

			PipedOutputStream pos = new PipedOutputStream();
			PipedInputStream pis = new PipedInputStream(pos,131072);
			channel.setInputStream(pis);
			pos.write(command.toString().getBytes());
			pos.flush();
			pos.close();
			InputStream in=channel.getInputStream();
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			channel.setOutputStream(baos);
			channel.connect();

			Thread.sleep(10000);
			String output = new String(baos.toByteArray()).replaceAll("\r", "");
			LOG.info("TSLU:: " + output);
			if (!output.contains("Statement executed successfully."))
				throw new TSLoadUtilityException(output, new Exception(output));
			channel.disconnect();
		} catch(JSchException | IOException | InterruptedException e) {
			LOG.error("TSLU:: " + e.getMessage());
			throw new TSLoadUtilityException(e.getMessage(),e);
		}
	}

	public void createSchema(String database, String schema) throws TSLoadUtilityException
	{
		StringBuilder command = new StringBuilder();
		command.append("tql\nuse " + database+";\ncreate schema "+schema+";\nexit;\nexit\n");
		LOG.info("TSLU:: " + command.toString());
		try {
			Channel channel=session.openChannel("shell");

			PipedOutputStream pos = new PipedOutputStream();
			PipedInputStream pis = new PipedInputStream(pos,131072);
			channel.setInputStream(pis);
			pos.write(command.toString().getBytes());
			pos.flush();
			pos.close();
			InputStream in=channel.getInputStream();
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			channel.setOutputStream(baos);
			channel.connect();

			Thread.sleep(10000);
			String output = new String(baos.toByteArray()).replaceAll("\r", "");
			LOG.info("TSLU:: " + output);
			if (!output.contains("Statement executed successfully."))
				throw new TSLoadUtilityException(output, new Exception(output));
			channel.disconnect();
		} catch(JSchException | IOException | InterruptedException e) {
			LOG.error("TSLU:: " + e.getMessage());
			throw new TSLoadUtilityException(e.getMessage(),e);
		}
	}

	public void createTable(LinkedHashMap<String, String> attributes, String schema, String table, String database) throws TSLoadUtilityException
	{
		StringBuilder command = new StringBuilder();
		command.append("tql\nuse " + database+";\ncreate table " + schema + "." + table + "(");
		int idx = 0;
		for (String key : attributes.keySet())
		{
			command.append(key +" " + attributes.get(key));
			if (idx++ != attributes.size() - 1)
				command.append(", ");
		}

		command.append(");\nexit;\nexit\n");
		LOG.info("TSLU:: " + command.toString());
		try {
			Channel channel=session.openChannel("shell");

			PipedOutputStream pos = new PipedOutputStream();
			PipedInputStream pis = new PipedInputStream(pos,131072);
			channel.setInputStream(pis);
			pos.write(command.toString().getBytes());
			pos.flush();
			pos.close();
			InputStream in=channel.getInputStream();
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			channel.setOutputStream(baos);
			channel.connect();

			Thread.sleep(10000);
			String output = new String(baos.toByteArray()).replaceAll("\r", "");
			LOG.info("TSLU:: " + output);
				if (!output.contains("Statement executed successfully."))
					throw new TSLoadUtilityException(output, new Exception(output));
			channel.disconnect();
		} catch(JSchException | IOException | InterruptedException e) {
			LOG.error("TSLU:: " + e.getMessage());
			throw new TSLoadUtilityException(e.getMessage(),e);
		}
	}
	
	public ArrayList<String> getSchemas(String database) throws TSLoadUtilityException {
		ArrayList<String> tables = new ArrayList<String>();
		try {
			Channel channel=session.openChannel("shell");
			
			PipedOutputStream pos = new PipedOutputStream();
	        PipedInputStream pis = new PipedInputStream(pos);
	        channel.setInputStream(pis);
	        pos.write(("tql\nuse "+database+";\nshow schemas;\nexit;\nexit\n").getBytes());
	        pos.flush();
	        pos.close();
	        InputStream in=channel.getInputStream();
	        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        channel.setOutputStream(baos);
	        channel.connect();
	        
	        Thread.sleep(10000);
	        String[] output = new String(baos.toByteArray()).replaceAll("\r", "").split("\n");
	        boolean flag = false;
	        
	        for (String line : output)
	        {
	        	if (flag && line.startsWith("Statement"))
	        	{
	        		flag = false;
	        	}
	        	if (flag)
	        	{
	        		String[] segments = line.split("\\|");
	        		tables.add(segments[0].trim());
	        	}
	        	if (line.startsWith("----"))
	        	{
	        		flag = true;
	        	}
	        	
	        }

	        channel.disconnect();
	        return tables;
		} catch(JSchException | IOException | InterruptedException e)
		{
			throw new TSLoadUtilityException(e.getMessage(),e);
		}
	}

	public ArrayList<String> getDatabases() throws TSLoadUtilityException {
		ArrayList<String> tables = new ArrayList<String>();
		try {
			Channel channel=session.openChannel("shell");
			
			PipedOutputStream pos = new PipedOutputStream();
	        PipedInputStream pis = new PipedInputStream(pos);
	        channel.setInputStream(pis);
	        pos.write(("tql\nshow databases;\nexit;\nexit\n").getBytes());
	        pos.flush();
	        pos.close();
	        InputStream in=channel.getInputStream();
	        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        channel.setOutputStream(baos);
	        channel.connect();
	        
	        Thread.sleep(10000);
	        String[] output = new String(baos.toByteArray()).replaceAll("\r", "").split("\n");
	        boolean flag = false;
	        
	        for (String line : output)
	        {
	        	if (flag && line.startsWith("Statement"))
	        	{
	        		flag = false;
	        	}
	        	if (flag)
	        	{
	        		
	        		tables.add(line.trim());
				}
				if (line.trim().startsWith("TQL [database=(none)]> show databases;"))
	        	{
	        		flag = true;
	        	}
	        	
	        }

	        channel.disconnect();
	        return tables;
		} catch(JSchException | IOException | InterruptedException e)
		{
			throw new TSLoadUtilityException(e.getMessage(), e);
		}
	}

	public ArrayList<String> getTables(String database, String schema) throws TSLoadUtilityException {
		ArrayList<String> tables = new ArrayList<String>();
		try {
			Channel channel=session.openChannel("shell");
			
			PipedOutputStream pos = new PipedOutputStream();
	        PipedInputStream pis = new PipedInputStream(pos);
	        channel.setInputStream(pis);
	        pos.write(("tql\nuse "+database+";\nshow tables;\nexit;\nexit\n").getBytes());
	        pos.flush();
	        pos.close();
	        InputStream in=channel.getInputStream();
	        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        channel.setOutputStream(baos);
	        channel.connect();
	        
	        Thread.sleep(10000);
	        String[] output = new String(baos.toByteArray()).replaceAll("\r", "").split("\n");
	        boolean flag = false;
	        
	        for (String line : output)
	        {
	        	if (flag && line.startsWith("Statement"))
	        	{
	        		flag = false;
	        	}
	        	if (flag)
	        	{
					String[] segments = line.split("\\|");
					if (segments[0].trim().equals(schema))
	        			tables.add(segments[1].trim());
	        	}
	        	if (line.startsWith("----"))
	        	{
	        		flag = true;
	        	}
	        	
	        }

	        channel.disconnect();
	        return tables;
		} catch(JSchException | IOException | InterruptedException e)
		{
			throw new TSLoadUtilityException(e.getMessage(), e);
		}
	}
	
	public void loadData(TSWriter writer,String tableName, int commit) throws TSLoadUtilityException {
		StringBuilder recs = new StringBuilder();
		int counter = 1;
		String threadName = writer.register(this.getClass().getSimpleName()+"::"+tableName+"::",ThreadStatus.RUNNING);
		while (!writer.getIsCompleted() || writer.size() > 0) {
			recs.setLength(0);
			LOG.info(threadName + " Outer Loop");
			while (counter <= commit && writer.size() > 0)
			{
				LOG.info(threadName + " Inner Loop, Counter " + counter);
				if (counter % 100 == 0)
					LOG.info(threadName + ": Records " + counter);
				if (writer.size() == 0) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						LOG.error(e.getMessage());
					}
				}
				if (writer.size() > 0)
				{
					recs.append(writer.poll()+"\n");
					counter++;
				}
			}
			counter = 1;
			/*
			for (String rec : records) {
				recs.append(rec + "\n");
			}*/
			String recsToLoad = recs.toString();
			//LOG.info(recsToLoad);
			//System.out.println(recsToLoad);
			if (recsToLoad.length() > 0) {
				//System.out.println(threadName + " Amount to load " + recsToLoad.length());

				try {
					Channel channel = session.openChannel("exec");
					ByteArrayOutputStream byteStream = new ByteArrayOutputStream(recsToLoad.length());
					GZIPOutputStream gos = new GZIPOutputStream(byteStream);
					gos.write(recsToLoad.getBytes());

					gos.flush();
					gos.close();
					byteStream.close();
					((ChannelExec) channel).setCommand(this.command);


					((ChannelExec) channel).setErrStream(System.err);

					PipedOutputStream pos = new PipedOutputStream();
					// getting the size of the compressed payload so only single write to server
					PipedInputStream pis = new PipedInputStream(pos, byteStream.toByteArray().length);
					channel.setInputStream(pis);

					pos.write(byteStream.toByteArray());
					pos.flush();
					pos.close();
					InputStream in = channel.getInputStream();

					//channel.setOutputStream(System.out);
					channel.connect();

					LOG.info(threadName + " Sending to TS Server");

					byte[] tmp = new byte[1024];
					StringBuilder results = new StringBuilder();
					while (true) {
						//System.out.println(threadName + "Checking Input");
						while (in.available() > 0) {
							int i = in.read(tmp, 0, 1024);
							if (i < 0) {
								break;
							}
							results.append(new String(tmp, 0, i));
							//System.out.print(threadName + " " + new String(tmp, 0, i));
						}
						//System.out.println(threadName + "Checking Channel");
						if (channel.isClosed()) {
							//System.out.println("exit-status: " + channel.getExitStatus());
							break;
						}


						try {
							//System.out.println(threadName + " Sleep");
							Thread.sleep(1000);
							//System.out.println(threadName + " Wake");
						} catch (Exception ee) {
							LOG.info(ee.getMessage());
						}
					}

					LOG.info(threadName + " Data Sent to TS Server");

					channel.disconnect();

					String[] result_lines = results.toString().split("\n");
					StringBuilder errors = new StringBuilder();
					int rows_total = 0;
					int rows_success = 0;
					int rows_failed = 0;
					int rows_dup_omitted = 0;
					boolean isSuccess = false;
					boolean summary = false;
					boolean started = false;
					try {
						for (int x = 0; x < result_lines.length; x++) {

							if (result_lines[x].startsWith("Status")) {
								isSuccess = result_lines[x].split(":")[1].trim().equalsIgnoreCase("Successful") ? true : false;
							} else if (result_lines[x].startsWith("Rows total")) {
								rows_total = Integer.parseInt(result_lines[x].split(":")[1].trim());
							} else if (result_lines[x].startsWith("Rows successfully")) {
								rows_success = Integer.parseInt(result_lines[x].split(":")[1].trim());
							} else if (result_lines[x].startsWith("Rows failed")) {
								rows_failed = Integer.parseInt(result_lines[x].split(":")[1].trim());
							} else if (result_lines[x].startsWith("Rows duplicate")) {
								rows_dup_omitted = Integer.parseInt(result_lines[x].split(":")[1].trim());
							} else if (result_lines[x].startsWith("Source summary")) {
								summary = true;
							} else if (result_lines[x].startsWith("Started processing data row")) {
								started = true;
							} else if (started && !summary) {
								errors.append(result_lines[x]);
							}
						}
						if (!isSuccess)
							throw new TSLoadUtilityException(errors.toString(),  new Exception(errors.toString()));
						if (rows_total != rows_success)
							throw new TSLoadUtilityException(rows_failed + " Failed Rows. " + rows_dup_omitted + " Rows Duplicated/Omitted. Message: " + errors.toString(),  new Exception(errors.toString()));
					} catch (Exception e) {
						LOG.info(e.getMessage());
					}
				} catch (JSchException | IOException e) {
					throw new TSLoadUtilityException(e.getMessage(), e);
				}
			} else {
				try {
					Thread.sleep(500);
				} catch(InterruptedException e) {}
			}
		}
		writer.update(threadName,ThreadStatus.DONE);
		System.out.println(threadName + " Done");
	}

	public void retrieve(String database, String table, TSReader reader) throws TSLoadUtilityException
	{
		//LinkedHashSet<String> records = new LinkedHashSet<>();
		System.out.println("Starting Read");
		try {
			Channel channel=session.openChannel("shell");

			PipedOutputStream pos = new PipedOutputStream();
			PipedInputStream pis = new PipedInputStream(pos);
			channel.setInputStream(pis);
			pos.write(("tql --query_results_apply_top_row_count 0 --null_string \"\"\nuse "+database+";\nselect * from "+table+";\nexit;\nexit\n").getBytes());
			pos.flush();
			pos.close();
			InputStream in=channel.getInputStream();
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			channel.setOutputStream(baos);
			channel.connect();

			Thread.sleep(10000);
			String[] output = new String(baos.toByteArray()).replaceAll("\r", "").split("\n");
			boolean flag = false;

			for (String line : output)
			{
				if (flag && line.startsWith("("))
				{
					flag = false;
				}
				if (flag)
				{
					reader.add(line);
				}
				if (line.startsWith("----"))
				{
					flag = true;
				}

			}

			reader.setIsCompleted(true);

			channel.disconnect();
			//return records;
		} catch(JSchException | IOException | InterruptedException e) {
			throw new TSLoadUtilityException(e.getMessage(), e);
		}
	}

	public void disconnect() {
        session.disconnect();
	}
}
