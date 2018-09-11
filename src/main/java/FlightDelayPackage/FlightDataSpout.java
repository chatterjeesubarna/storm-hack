package FlightDelayPackage;

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

import java.io.*;
import java.util.*;

public class FlightDataSpout extends BaseRichSpout implements IRichSpout{

	public static Logger LOG = LoggerFactory.getLogger(FlightDataSpout.class);
	SpoutOutputCollector _collector;
	Scanner fileInput;
	int count =-1, total =0;
	ArrayList<ArrayList<String>> array = new ArrayList<ArrayList<String>>();
	public FlightDataSpout() {
	}

	public void nextTuple() {
		Utils.sleep(10); 
		//if(fileInput.hasNextLine()){
		if(total < 4000){
			String obtained = fileInput.nextLine();
			if(!obtained.contains("ArrDelay"))
			{
				String[] oneLine = obtained.split(",");
				String time = String.valueOf(System.nanoTime());
				final ArrayList<String> line = new ArrayList(Arrays.asList(oneLine));
				line.add(time);
				array.add(line);
				total++;
				_collector.emit(new Values(line));
			}
		}
		else
		{
			count = (count + 1)%array.size();
			_collector.emit(new Values(array.get(count)));
		}
	}

	public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {

		_collector = collector;
		try{
			//fileInput = new Scanner(new File(conf.get("2008_small.csv").toString()));
			fileInput = new Scanner(new File(conf.get("2008.csv").toString()));
		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}

	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("tuple"));

	}
}