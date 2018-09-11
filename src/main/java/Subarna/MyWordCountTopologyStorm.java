package Subarna;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

//import com.twitter.heron.spi.common.Constants;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.IRichSpout;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;



//import com.twitter.heron.spi.packing.*;


public class MyWordCountTopologyStorm {

	public static class SplitSentence extends BaseRichBolt {

		private OutputCollector collector;
		private String taskName;

		public void prepare(Map stormConf, TopologyContext context,
				OutputCollector collector) {
			this.collector = collector;
			taskName = context.getThisComponentId() + "_" + context.getThisTaskId();
		}

		public void execute(Tuple tuple) {
			String sentence = tuple.getString(0);
			for (String word : sentence.split(" ")) {
				//System.out.println("In split bolt, " +  taskName + ":" + word);
				
				/*Process p;
				try {
					p = Runtime.getRuntime().exec("stress --cpu 1 --timeout 1s");
					p.waitFor();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
				collector.emit(tuple, new Values(word));
			}
			collector.ack(tuple);
		}

		public void declareOutputFields(OutputFieldsDeclarer declarer) {
			declarer.declare(new Fields("word"));
		}

	}
	private MyWordCountTopologyStorm() { }

	public static class WordCount extends BaseRichBolt {

		Map<String, Integer> counts = new HashMap<String, Integer>();
		private OutputCollector collector;
		private int tupleCount;
		private String taskName;

		public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
			collector = outputCollector;
			counts = new HashMap<String, Integer>();
			tupleCount = 0;
			taskName = topologyContext.getThisComponentId() + "_" + topologyContext.getThisTaskId();
		}

		@Override
		public void execute(Tuple tuple) {
			String key = tuple.getString(0);
			tupleCount += 1;
			if (tupleCount % 200 == 0) {
				tupleCount = 0;
				System.out.println("In count bolt, " +  taskName + ":" + Arrays.toString(counts.entrySet().toArray()));
				/*Process p;
				try {
					p = Runtime.getRuntime().exec("stress --cpu 1 --timeout 1s");
					p.waitFor();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
			}

			if (counts.get(key) == null) {
				counts.put(key, 1);
			} else {
				Integer val = counts.get(key);
				counts.put(key, ++val);
			}

			collector.ack(tuple);
		}		

		@Override
		public void declareOutputFields(OutputFieldsDeclarer declarer) {
			declarer.declare(new Fields("word", "count"));
		}

	}


	public static void main(String[] args) throws Exception {


		TopologyBuilder builder = new TopologyBuilder();
		 
		builder.setSpout("spout", new RandomSentenceSpout(), 75);
		builder.setBolt("split", new SplitSentence(), 75).shuffleGrouping("spout");
		builder.setBolt("count", new WordCount(), 75).fieldsGrouping("split", new Fields("word"));

		Config conf = new Config();
		conf.setDebug(true);

		if (args != null && args.length > 0) {
			conf.setNumWorkers(6);
			StormSubmitter.submitTopology(args[0], conf, builder.createTopology());
		}
		else {
			conf.setMaxTaskParallelism(3);
			LocalCluster cluster = new LocalCluster();
			cluster.submitTopology("word-count", conf, builder.createTopology());

			Thread.sleep(10000);

			cluster.shutdown();
		}
	}
}