package com.github.sawied.crawler.components;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.storm.spout.Scheme;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpebble.stormcrawler.persistence.AbstractQueryingSpout;
import com.digitalpebble.stormcrawler.sql.Constants;
import com.digitalpebble.stormcrawler.sql.SQLUtil;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.digitalpebble.stormcrawler.util.StringTabScheme;

/**
 * use Mysql as data source and custom parent behavior
 * 
 * @author sawied
 *
 */
public class MySQLSpout extends AbstractQueryingSpout {

	private static final long serialVersionUID = -8888962609509479371L;

	public static final Logger LOG = LoggerFactory.getLogger(MySQLSpout.class);

	private static final Scheme SCHEME = new StringTabScheme();

	private String tableName;

	private Connection connection;

	/**
	 * if more than one instance of the spout exist, each one is in charge of a
	 * separate bucket value. This is used to ensure a good diversity of URLs.
	 **/
	private int bucketNum = -1;

	/** Used to distinguish between instances in the logs **/
	protected String logIdprefix = "";

	private int maxDocsPerBucket;

	private int maxNumResults;

	private Instant lastNextFetchDate = null;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {

		super.open(conf, context, collector);

		maxDocsPerBucket = ConfUtils.getInt(conf, Constants.SQL_MAX_DOCS_BUCKET_PARAM_NAME, 5);

		tableName = ConfUtils.getString(conf, Constants.SQL_STATUS_TABLE_PARAM_NAME, "urls");

		maxNumResults = ConfUtils.getInt(conf, Constants.SQL_MAXRESULTS_PARAM_NAME, 100);

		try {
			connection = SQLUtil.getConnection(conf);
		} catch (SQLException ex) {
			LOG.error(ex.getMessage(), ex);
			throw new RuntimeException(ex);
		}

		// determine bucket this spout instance will be in charge of
		int totalTasks = context.getComponentTasks(context.getThisComponentId()).size();
		if (totalTasks > 1) {
			logIdprefix = "[" + context.getThisComponentId() + " #" + context.getThisTaskIndex() + "] ";
			bucketNum = context.getThisTaskIndex();
		}
	}

	@Override
	protected void populateBuffer() {
		
		  if (lastNextFetchDate == null) {
	            lastNextFetchDate = Instant.now();
	            lastTimeResetToNOW = Instant.now();
	        } else if (resetFetchDateAfterNSecs != -1) {
	            Instant changeNeededOn = Instant.ofEpochMilli(lastTimeResetToNOW
	                    .toEpochMilli() + (resetFetchDateAfterNSecs * 1000));
	            if (Instant.now().isAfter(changeNeededOn)) {
	                LOG.info("lastDate reset based on resetFetchDateAfterNSecs {}",
	                        resetFetchDateAfterNSecs);
	                lastNextFetchDate = Instant.now();
	            }
	        }
		  
		  
		  String query = "SELECT * from (select rank() over (partition by host order by nextfetchdate desc, url) as ranking, url, metadata, nextfetchdate,correlationid from "
	                + tableName;

	        query += " WHERE nextfetchdate <= '"
	                + new Timestamp(lastNextFetchDate.toEpochMilli()) + "'";

	        // constraint on bucket num
	        if (bucketNum >= 0) {
	            query += " AND bucket = '" + bucketNum + "'";
	        }

	        query += ") as urls_ranks where (urls_ranks.ranking <= "
	                + maxDocsPerBucket + ") order by ranking";

	        if (maxNumResults != -1) {
	            query += " LIMIT " + this.maxNumResults;
	        }

	        int alreadyprocessed = 0;
	        int numhits = 0;

	        long timeStartQuery = System.currentTimeMillis();

	        // create the java statement
	        Statement st = null;
	        ResultSet rs = null;
	        try {
	            st = this.connection.createStatement();

	            // dump query to log
	            LOG.debug("{} SQL query {}", logIdprefix, query);

	            // execute the query, and get a java resultset
	            rs = st.executeQuery(query);

	            long timeTaken = System.currentTimeMillis() - timeStartQuery;
	            queryTimes.addMeasurement(timeTaken);

	            // iterate through the java resultset
	            while (rs.next()) {
	                String url = rs.getString("url");
	                numhits++;
	                // already processed? skip
	                if (beingProcessed.containsKey(url)) {
	                    alreadyprocessed++;
	                    continue;
	                }
	                
	                String correlationId=rs.getString("correlationid");
	                
	                String metadata = rs.getString("metadata");
	                if (metadata == null) {
	                    metadata = "\t"+"correlationId="+correlationId;
	                } else if (!metadata.startsWith("\t")) {
	                    metadata = "\t" + metadata;
	                }
	                String URLMD = url + metadata;
	                List<Object> v = SCHEME.deserialize(ByteBuffer.wrap(URLMD
	                        .getBytes()));
	                Values vals = new Values();
	                vals.addAll(v);
	                buffer.add(vals);
	            }

	            // no results? reset the date
	            if (numhits == 0) {
	                lastNextFetchDate = null;
	            }

	            eventCounter.scope("already_being_processed").incrBy(
	                    alreadyprocessed);
	            eventCounter.scope("queries").incrBy(1);
	            eventCounter.scope("docs").incrBy(numhits);

	            LOG.info(
	                    "{} SQL query returned {} hits in {} msec with {} already being processed",
	                    logIdprefix, numhits, timeTaken, alreadyprocessed);

	        } catch (SQLException e) {
	            LOG.error("Exception while querying table", e);
	        } finally {
	            try {
	                if (rs != null)
	                    rs.close();
	            } catch (SQLException e) {
	                LOG.error("Exception closing resultset", e);
	            }
	            try {
	                if (st != null)
	                    st.close();
	            } catch (SQLException e) {
	                LOG.error("Exception closing statement", e);
	            }
	        }

	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(SCHEME.getOutputFields());
	}

}
