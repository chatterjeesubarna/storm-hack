
package AirQualityMonitor;


import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichSpout;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.*;
import java.util.*;

public class O3Spout extends BaseRichSpout implements IRichSpout{

	public static Logger LOG = LoggerFactory.getLogger(O3Spout.class);
	boolean _isDistributed;
	SpoutOutputCollector _collector;
	boolean completed = false;
	Scanner fileInput;
	String filename;
	ArrayList<ArrayList<String>> lines = new ArrayList<ArrayList<String>>();
	int count=-1;
	int total=0;

	public O3Spout() {
	}
	
	public void nextTuple() {
		Utils.sleep(10); 
		try{
			if(fileInput.hasNextLine()){
				//String time = String.valueOf(System.currentTimeMillis());
				String time = String.valueOf(System.nanoTime());
				String[] oneLine = fileInput.nextLine().split(",");


				final ArrayList<String> line = new ArrayList(Arrays.asList(oneLine));
				line.add(time);
				lines.add(line);
				total++;
				_collector.emit(new Values(line));
			}
			else
			{
				count = (count + 1)%lines.size();
				total++;
				//if(total <= 10000)
				_collector.emit(new Values(lines.get(count)));
			}
		}catch(Exception e){
			throw new RuntimeException(e.getMessage());
		}
	}

	public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {
		
		_collector = collector;
		try{
			fileInput = new Scanner(new File(conf.get("O3").toString()));
		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}

	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("tuple"));

	}

}

