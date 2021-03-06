package com.agartime.utad.mapreduce;

import com.datasalt.pangool.io.ITuple;
import com.datasalt.pangool.io.Schema;
import com.datasalt.pangool.io.Schema.Field;
import com.datasalt.pangool.io.Tuple;
import com.datasalt.pangool.serialization.ThriftSerialization;
import com.datasalt.pangool.tuplemr.*;
import com.datasalt.pangool.tuplemr.mapred.lib.input.HadoopInputFormat;
import com.datasalt.pangool.tuplemr.mapred.lib.output.HadoopOutputFormat;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: antoniogonzalezartime
 * Date: 26/11/13
 * Time: 20:31
 * To change this template use File | Settings | File Templates.
 */
public class TopTemperatureChanges extends Configured implements Tool {

    public static Schema getSchema() {
        ArrayList<Field> fields = new ArrayList<Field>();
        fields.add(Field.create("location", Field.Type.STRING));
        fields.add(Field.create("day", Field.Type.INT));
        fields.add(Field.create("temperature_change", Field.Type.FLOAT));
        fields.add(Field.create("temperature_absolute_change", Field.Type.FLOAT));
        return new Schema("Changes", fields);
    }

    @SuppressWarnings("serial")
    public static class ParseMap extends TupleMapper<Temperature, NullWritable> {

        Tuple tuple = new Tuple(getSchema());

        @Override
        public void map(Temperature temperature, NullWritable emptyValue, TupleMRContext context,
                        Collector collector) throws IOException, InterruptedException {

            tuple.set("location", temperature.location);
            tuple.set("day", temperature.day);
            tuple.set("temperature_change", temperature.temperature);
            //Here we insert the absolute value of the temperature change.
            tuple.set("temperature_absolute_change", Math.abs(new Float(temperature.temperature)));

            collector.write(tuple);
        }

    }

    @SuppressWarnings("serial")
    public static class DifferencesReducer extends TupleReducer<Text, NullWritable> {

        private int topValue;

        // Because in Pangool, reducers are an instance and not a singleton, we can create
        // a custom constructor receiving a custom topValue as parameter.
        public DifferencesReducer(int topValue) {
            this.topValue = topValue;
        }

        //If we weren't using Pangool, we could receive a parameter to the reducer using Configuration and Hadoop Context
        /*public void setup(TupleMRContext tupleMRContext, Collector collector) throws InterruptedException, TupleMRException, IOException {
            super.setup(tupleMRContext, collector);
            Configuration conf = tupleMRContext.getHadoopContext().getConfiguration();
            this.topValue = Integer.parseInt(conf.get("top_value"));
        }
        */

        public void reduce(ITuple group, Iterable<ITuple> tuples, TupleMRContext context,
                           Collector collector) throws IOException, InterruptedException, TupleMRException {

            int count = 0;
            for (ITuple tuple : tuples) {
                String out = tuple.get("location")+" "+tuple.get("day")+" "+tuple.get("temperature_change");
                collector.write(new Text(out), NullWritable.get());

                if (topValue == ++count) {
                    break;
                }
            }
        };
    }

    @Override
    public int run(String[] args) throws Exception {
        if(args.length != 3) {
            System.out.println("Invalid number of arguments\n\n" +
                    "Usage: top-temperature-changes <input_path> <output_path> <top_value>\n\n");
            return -1;
        }
        String input = args[0];
        String output = args[1];
        Integer topValue = new Integer(args[2]);

        Path oPath = new Path(output);
        FileSystem.get(oPath.toUri(), getConf()).delete(oPath, true);

        //If we weren't using Pangool, we could pass a parameter to the reducer injecting it like this:
        //getConf().set("top_value",topValue);

        TupleMRBuilder mr = new TupleMRBuilder(getConf(), "Temperature changes");
        mr.setJarByClass(TopTemperatureChanges.class);
        mr.addIntermediateSchema(getSchema());
        mr.setGroupByFields("location");
        mr.setOrderBy(new OrderBy().add("location", Criteria.Order.ASC).add("temperature_absolute_change", Criteria.Order.DESC));
        mr.setTupleReducer(new DifferencesReducer(topValue));
        mr.addInput(new Path(input), new HadoopInputFormat(SequenceFileInputFormat.class), new ParseMap());
        mr.setOutput(new Path(output), new HadoopOutputFormat(TextOutputFormat.class), Text.class, NullWritable.class);
        Job job = mr.createJob();
        ThriftSerialization.enableThriftSerialization(job.getConfiguration());
        job.waitForCompletion(true);

        return 0;
    }

    public static void main(String args[]) throws Exception {
        ToolRunner.run(new TopTemperatureChanges(), args);
    }
}