package FlightDelayPackage;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.task.OutputCollector;
//import backtype.storm.task.ShellBolt;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

import java.io.*;
import java.util.*;

import com.esotericsoftware.kryo.Kryo;
import com.twitter.heron.spi.common.Constants;

import FlightDelayPackage.FlightDelayAnalysisTopology.CarrierReducedTupleBolt;
import FlightDelayPackage.FlightDelayAnalysisTopology.DayDelayRankingBolt;
import FlightDelayPackage.FlightDelayAnalysisTopology.DayDelaySumBolt;
import FlightDelayPackage.FlightDelayAnalysisTopology.DayReducedTupleBolt;
import FlightDelayPackage.FlightDelayAnalysisTopology.DelayRankingBolt;
import FlightDelayPackage.FlightDelayAnalysisTopology.DelaySumBolt;


//g5k
//heron submit aurora/root/devel --config-path ~/.heron/conf/ /root/storm-hack/target/storm-hack-0.0.1-SNAPSHOT-jar-with-dependencies.jar 
// FlightDelayPackage.FlightDelayAnalysisTopology FlightDelayAnalysisTopology "/root/storm-hack/2008.csv"

// Local mc
// bin/storm jar ~/workspace/storm-starter/target/storm-starter-0.10.2.jar FlightDelayPackage.FlightDelayAnalysisTopology 
// FlightDelayAnalysisTopology "/home/suchatte/workspace/storm-hack/2008.csv"

public class FlightDelayAnalysisTopology {

	public static HashMap<String, ArrayList<ArrayList<String>>> carrier_details = new HashMap<String, ArrayList<ArrayList<String>>>();
	public static HashMap<String, Integer> carrier_integer_mapping = new HashMap<String, Integer>();
	public static int carrierNo = 0;

	public static class DayReducedTupleBolt extends BaseRichBolt {
		private OutputCollector collector;
		private String taskName;

		public void prepare(Map stormConf, TopologyContext context,
				OutputCollector collector) {
			this.collector = collector;
			taskName = context.getThisComponentId() + "_" + context.getThisTaskId();
		}

		public void execute(Tuple tuple) {
			ArrayList raw =new ArrayList((ArrayList)tuple.getValue(0));
			ArrayList<String> reducedTuple = new ArrayList<String>();
			int day = Integer.parseInt(raw.get(3).toString()); 	//DayOfWeek 	1 (Monday) - 7 (Sunday)
			reducedTuple.add(raw.get(8).toString()); 	//	unique carrier code
			reducedTuple.add(raw.get(14).toString()); 	//arrival delay, in minutes
			reducedTuple.add(raw.get(15).toString()); 	//	departure delay, in minutes
			reducedTuple.add(raw.get(24).toString()); 	//		CarrierDelay 	in minutes
			reducedTuple.add(raw.get(25).toString()); 	//		WeatherDelay 	in minutes
			reducedTuple.add(raw.get(26).toString()); 	//			NASDelay 	in minutes
			reducedTuple.add(raw.get(27).toString()); 	//		SecurityDelay 	in minutes
			reducedTuple.add(raw.get(28).toString()); 	//			LateAircraftDelay 	in minutes

			String carrier = raw.get(8).toString();
			if (carrier_details.containsKey(carrier))
			{
				carrier_details.get(carrier).add(raw);
				carrierNo++;
				carrier_integer_mapping.put(carrier, carrierNo);
			}
			else
			{
				carrier_details.put(carrier, new ArrayList<ArrayList<String>>());
				carrier_details.get(carrier).add(raw);
			}
			//System.out.println(reducedTuple);	
			collector.emit(tuple, new Values(day, reducedTuple));
			collector.ack(tuple);
		}

		public void declareOutputFields(OutputFieldsDeclarer declarer) {
			declarer.declare(new Fields("day", "details"));
		}
	}

	public static class DayDelaySumBolt extends BaseRichBolt {
		private OutputCollector collector;
		private String taskName;
		HashMap<Integer, HashMap<String, int[][]>> hmap1 = new HashMap<Integer, HashMap<String, int[][]>>();

		public void prepare(Map stormConf, TopologyContext context,
				OutputCollector collector) {
			this.collector = collector;
			taskName = context.getThisComponentId() + "_" + context.getThisTaskId();
		}

		public void execute(Tuple tuple) {
			ArrayList raw =new ArrayList((ArrayList)tuple.getValue(1));
			int day = Integer.parseInt(tuple.getValue(0).toString());
			String carrier = raw.get(0).toString();
			if(!hmap1.containsKey(day))
			{
				HashMap<String, int[][]> hmap = new HashMap<String, int[][]>();
				int temp[][] = new int[7][2];
				hmap.put(carrier, temp);
				hmap1.put(day,  hmap);
			}
			//System.out.println(day);

			for(int i =1;i<raw.size();i++)
			{
				String delay = raw.get(i).toString();
				if (delay.equals("NA"))
					delay = "0";
				int d = Integer.parseInt(delay);
				if (d > 0)
				{
					HashMap<String, int[][]> hmap = hmap1.get(day);
					int[][] array = hmap.get(carrier);
					array[i-1][0] = array[i-1][0] + 1;
					array[i-1][1] =  array[i-1][1] + d;
					hmap.put(carrier, array);
					hmap1.put(day,  hmap);
				}
			}
			collector.emit(tuple, new Values(hmap1));
			collector.ack(tuple);
		}
		public void declareOutputFields(OutputFieldsDeclarer declarer) {
			declarer.declare(new Fields("day-delay-score"));
		}
	}


	public static class DayDelayRankingBolt extends BaseRichBolt {
		private OutputCollector collector;
		private String taskName;

		public void prepare(Map stormConf, TopologyContext context,
				OutputCollector collector) {
			this.collector = collector;
			taskName = context.getThisComponentId() + "_" + context.getThisTaskId();
		}

		public void execute(Tuple tuple) {
			HashMap<Integer, HashMap<String, int[][]>> raw =new HashMap((HashMap)tuple.getValue(0));
			HashMap <Integer, HashMap<String, Double>> arrDelay  = new HashMap <Integer, HashMap<String, Double>>();
			HashMap <Integer, HashMap<String, Double>> depDelay  = new HashMap <Integer, HashMap<String, Double>>();
			HashMap <Integer, HashMap<String, Double>> carrierDelay  = new HashMap <Integer, HashMap<String, Double>>();
			HashMap <Integer, HashMap<String, Double>> weatherDelay  = new HashMap <Integer, HashMap<String, Double>>();
			HashMap <Integer, HashMap<String, Double>> NASDelay  = new HashMap <Integer, HashMap<String, Double>>();
			HashMap <Integer, HashMap<String, Double>> securityDelay  = new HashMap <Integer, HashMap<String, Double>>();
			HashMap <Integer, HashMap<String, Double>> lateDelay  = new HashMap <Integer, HashMap<String, Double>>();

			for(int day: raw.keySet())
			{
				HashMap<String, int[][]> hmap = raw.get(day);
				for(String carrier: hmap.keySet())
				{
					int[][] array = hmap.get(carrier);
					if(!arrDelay.containsKey(day))
					{
						HashMap<String, Double> hmap2 = new HashMap<String, Double>();
						hmap2.put(carrier, 0.0);
						arrDelay.put(day, hmap2);
					}
					if(arrDelay.get(day).containsKey(carrier) && array[0][0]!=0)
					{
						arrDelay.get(day).put(carrier, (double)array[0][1]/array[0][0]);
					}

					if(!depDelay.containsKey(day))
					{
						HashMap<String, Double> hmap2 = new HashMap<String, Double>();
						hmap2.put(carrier, 0.0);
						depDelay.put(day, hmap2);
					}
					if(depDelay.get(day).containsKey(carrier) && array[0][0]!=0)
					{
						depDelay.get(day).put(carrier, (double)array[0][1]/array[0][0]);
					}

					if(!carrierDelay.containsKey(day))
					{
						HashMap<String, Double> hmap2 = new HashMap<String, Double>();
						hmap2.put(carrier, 0.0);
						carrierDelay.put(day, hmap2);
					}
					if(carrierDelay.get(day).containsKey(carrier) && array[0][0]!=0)
					{
						carrierDelay.get(day).put(carrier, (double)array[0][1]/array[0][0]);
					}

					if(!weatherDelay.containsKey(day))
					{
						HashMap<String, Double> hmap2 = new HashMap<String, Double>();
						hmap2.put(carrier, 0.0);
						weatherDelay.put(day, hmap2);
					}
					if(weatherDelay.get(day).containsKey(carrier) && array[0][0]!=0)
					{
						weatherDelay.get(day).put(carrier, (double)array[0][1]/array[0][0]);
					}

					if(!NASDelay.containsKey(day))
					{
						HashMap<String, Double> hmap2 = new HashMap<String, Double>();
						hmap2.put(carrier, 0.0);
						NASDelay.put(day, hmap2);
					}
					if(NASDelay.get(day).containsKey(carrier) && array[0][0]!=0)
					{
						NASDelay.get(day).put(carrier, (double)array[0][1]/array[0][0]);
					}

					if(!securityDelay.containsKey(day))
					{
						HashMap<String, Double> hmap2 = new HashMap<String, Double>();
						hmap2.put(carrier, 0.0);
						securityDelay.put(day, hmap2);
					}
					if(securityDelay.get(day).containsKey(carrier) && array[0][0]!=0)
					{
						securityDelay.get(day).put(carrier, (double)array[0][1]/array[0][0]);
					}

					if(!lateDelay.containsKey(day))
					{
						HashMap<String, Double> hmap2 = new HashMap<String, Double>();
						hmap2.put(carrier, 0.0);
						lateDelay.put(day, hmap2);
					}
					if(lateDelay.get(day).containsKey(carrier) && array[0][0]!=0)
					{
						lateDelay.get(day).put(carrier, (double)array[0][1]/array[0][0]);
					}
				}
			}
			System.out.println("arrDelay: " + arrDelay);
			System.out.println("depDelay: " + depDelay);	
			System.out.println("carrierDelay: " + carrierDelay);	
			System.out.println("weatherDelay: " + weatherDelay);	
			System.out.println("NASDelay: " + NASDelay);	
			System.out.println("securityDelay: " + securityDelay);	
			System.out.println("lateDelay: " + lateDelay);	
			collector.ack(tuple);
		}

		public void declareOutputFields(OutputFieldsDeclarer declarer) {
			declarer.declare(new Fields("day-delay-rank"));
		}
	}

	public static class CarrierReducedTupleBolt extends BaseRichBolt {
		private OutputCollector collector;
		private String taskName;

		public void prepare(Map stormConf, TopologyContext context,
				OutputCollector collector) {
			this.collector = collector;
			taskName = context.getThisComponentId() + "_" + context.getThisTaskId();
		}

		public void execute(Tuple tuple) {
			ArrayList raw =new ArrayList((ArrayList)tuple.getValue(0));
			ArrayList<String> reducedTuple = new ArrayList<String>();
			reducedTuple.add(raw.get(8).toString()); 	//	unique carrier code
			reducedTuple.add(raw.get(14).toString()); 	//arrival delay, in minutes
			reducedTuple.add(raw.get(15).toString()); 	//	departure delay, in minutes
			reducedTuple.add(raw.get(24).toString()); 	//		CarrierDelay 	in minutes
			reducedTuple.add(raw.get(25).toString()); 	//		WeatherDelay 	in minutes
			reducedTuple.add(raw.get(26).toString()); 	//			NASDelay 	in minutes
			reducedTuple.add(raw.get(27).toString()); 	//		SecurityDelay 	in minutes
			reducedTuple.add(raw.get(28).toString()); 	//			LateAircraftDelay 	in minutes

			String carrier = raw.get(8).toString();
			if (carrier_details.containsKey(carrier))
			{
				carrier_details.get(carrier).add(raw);
				carrierNo++;
				carrier_integer_mapping.put(carrier, carrierNo);
			}
			else
			{
				carrier_details.put(carrier, new ArrayList<ArrayList<String>>());
				carrier_details.get(carrier).add(raw);
			}
			//System.out.println(reducedTuple);	
			collector.emit(tuple, new Values(carrier, reducedTuple));
			collector.ack(tuple);
		}

		public void declareOutputFields(OutputFieldsDeclarer declarer) {
			declarer.declare(new Fields("carrier", "carrier-flights"));
		}
	}

	public static class DelaySumBolt extends BaseRichBolt {
		private OutputCollector collector;
		private String taskName;
		HashMap<String, int[][]> hmap = new HashMap<String, int[][]>();

		public void prepare(Map stormConf, TopologyContext context,
				OutputCollector collector) {
			this.collector = collector;
			taskName = context.getThisComponentId() + "_" + context.getThisTaskId();
		}

		public void execute(Tuple tuple) {
			ArrayList raw =new ArrayList((ArrayList)tuple.getValue(1));
			String carrier = raw.get(0).toString();
			//System.out.println(raw);;
			if(!hmap.containsKey(carrier))
			{
				int temp[][] = new int[7][2];
				hmap.put(carrier, temp);
			}
			for(int i =1;i<raw.size();i++)
			{
				String delay = raw.get(i).toString();
				if (delay.equals("NA"))
					delay = "0";
				int d = Integer.parseInt(delay);
				if (d > 0)
				{
					int[][] array = hmap.get(carrier);
					array[i-1][0] = array[i-1][0] + 1;
					array[i-1][1] =  array[i-1][1] + d;
					hmap.put(carrier, array);
				}
			}

			collector.emit(tuple, new Values(hmap));
			collector.ack(tuple);
		}
		public void declareOutputFields(OutputFieldsDeclarer declarer) {
			declarer.declare(new Fields("delay-score"));
		}
	}

	public static class DelayRankingBolt extends BaseRichBolt {
		private OutputCollector collector;
		private String taskName;
		HashMap<String, int[]> hmap = new HashMap<String, int[]>();

		public void prepare(Map stormConf, TopologyContext context,
				OutputCollector collector) {
			this.collector = collector;
			taskName = context.getThisComponentId() + "_" + context.getThisTaskId();
		}

		public void execute(Tuple tuple) {
			HashMap<String, int[][]> raw =new HashMap((HashMap)tuple.getValue(0));
			int noOfCarrier = raw.size();
			HashMap<String, Double> arrDelay  = new HashMap<String, Double>();
			HashMap<String, Double> depDelay  = new HashMap<String, Double>();
			HashMap<String, Double> carrierDelay  = new HashMap<String, Double>();
			HashMap<String, Double> weatherDelay  = new HashMap<String, Double>();
			HashMap<String, Double> NASDelay  = new HashMap<String, Double>();
			HashMap<String, Double> securityDelay  = new HashMap<String, Double>();
			HashMap<String, Double> lateDelay  = new HashMap<String, Double>();

			for(String carrier: raw.keySet())
			{
				int[][] array = raw.get(carrier);
				if(!arrDelay.containsKey(carrier) && array[0][0]!=0)
				{
					arrDelay.put(carrier, (double)array[0][1]/array[0][0]);
				}
				if(!depDelay.containsKey(carrier) && array[1][0]!=0)
				{
					depDelay.put(carrier, (double)array[1][1]/array[1][0]);
				}
				if(!carrierDelay.containsKey(carrier) && array[2][0]!=0)
				{
					carrierDelay.put(carrier, (double)array[2][1]/array[2][0]);
				}
				if(!weatherDelay.containsKey(carrier) && array[3][0]!=0)
				{
					weatherDelay.put(carrier, (double)array[3][1]/array[3][0]);
				}
				if(!NASDelay.containsKey(carrier) && array[4][0]!=0)
				{
					NASDelay.put(carrier, (double)array[4][1]/array[4][0]);
				}
				if(!securityDelay.containsKey(carrier) && array[5][0]!=0)
				{
					securityDelay.put(carrier, (double)array[5][1]/array[5][0]);
				}
				if(!lateDelay.containsKey(carrier) && array[6][0]!=0)
				{
					lateDelay.put(carrier, (double)array[6][1]/array[6][0]);
				}
			}

			System.out.println("arrDelay: " + arrDelay);	
			System.out.println("depDelay: " + depDelay);	
			System.out.println("carrierDelay: " + carrierDelay);	
			System.out.println("weatherDelay: " + weatherDelay);	
			System.out.println("NASDelay: " + NASDelay);	
			System.out.println("securityDelay: " + securityDelay);	
			System.out.println("lateDelay: " + lateDelay);	
			collector.ack(tuple);
		}

		public void declareOutputFields(OutputFieldsDeclarer declarer) {
			declarer.declare(new Fields("delay-rank"));
		}
	}

	public static void main(String[] args) throws Exception {

		TopologyBuilder builder = new TopologyBuilder();
		builder.setSpout("DataSpout", new FlightDataSpout(), 10);

		builder.setBolt("carrier-filter", new CarrierReducedTupleBolt(), 13).shuffleGrouping("DataSpout"); 
		//builder.setBolt("delay-sum", new DelaySumBolt(), 30).fieldsGrouping("carrier-filter", new Fields("carrier"));
		builder.setBolt("delay-sum", new DelaySumBolt(), 8).shuffleGrouping("carrier-filter");
		builder.setBolt("delay-rank", new DelayRankingBolt(), 8).shuffleGrouping("delay-sum");

		builder.setBolt("day-filter", new DayReducedTupleBolt(), 13).shuffleGrouping("DataSpout");
		//builder.setBolt("day-delay-sum", new DayDelaySumBolt(), 30).fieldsGrouping("day-filter", new Fields("day"));
		builder.setBolt("day-delay-sum", new DayDelaySumBolt(), 8).shuffleGrouping("day-filter");
		builder.setBolt("day-delay-rank", new DayDelayRankingBolt(), 8).shuffleGrouping("day-delay-sum");

		Config conf = new Config();
		conf.put("2008.csv",args[1]);
		conf.registerSerialization(int[][].class);
		conf.registerSerialization(int[].class);


		if (args != null && args.length > 0) {
			conf.setNumWorkers(6);
			conf.setContainerCpuRequested(6f);
			conf.setContainerDiskRequested(4L * Constants.GB);
			conf.setContainerRamRequested(10L * Constants.GB);	
			StormSubmitter.submitTopology(args[0], conf, builder.createTopology());

			/*conf.setNumWorkers(3);
			LocalCluster cluster = new LocalCluster();
			cluster.submitTopology("flight-delay-analysis", conf, builder.createTopology());
			System.out.println("Submitted + " + conf);
			Thread.sleep(10000);*/

			//cluster.shutdown();*/
		}
		else {
			conf.setMaxTaskParallelism(3);
			LocalCluster cluster = new LocalCluster();
			cluster.submitTopology("flight-delay-analysis", conf, builder.createTopology());
			System.out.println("Submitted + " + conf);
			Thread.sleep(10000);

			cluster.shutdown();
		}

	}

}
