package com.mikemunhall.flumerlog4j;

import java.io.OutputStream;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.serialization.EventSerializer;
import org.apache.flume.serialization.AbstractAvroEventSerializer;
import com.google.common.base.Charsets;

public class FlumeEventStringBodySerializer extends AbstractAvroEventSerializer<FlumeEventStringBodySerializer.Container> {

    /*
        {
          "type" : "record",
          "name" : "Event",
          "fields" : [
              {
                "name" : "body",
                "type" : "string"
              }, {
                "name" : "header_timestamp",
                "type" : "long"
              }, {
                "name" : "header_guid",
                "type" : "string"
              }
            ]
        }
    */
    private static final String SCHEMA_STRING = "{\n" +
            "  \"type\" : \"record\",\n" +
            "  \"name\" : \"Event\",\n" +
            "  \"fields\" : [\n" +
            "    {\n" +
            "      \"name\" : \"body\",\n" +
            "      \"type\" : \"string\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\" : \"header_timestamp\",\n" +
            "      \"type\" : \"long\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\" : \"header_guid\",\n" +
            "      \"type\" : \"string\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    private static final Schema SCHEMA = new Schema.Parser().parse(SCHEMA_STRING);

    private final OutputStream out;

    private FlumeEventStringBodySerializer(OutputStream out) {
        this.out = out;
    }

    @Override
    protected Schema getSchema() {
        return SCHEMA;
    }

    @Override
    protected OutputStream getOutputStream() {
        return out;
    }

    @Override
    protected Container convert(Event event) {
        return new Container(event.getHeaders(), new String(event.getBody(), Charsets.UTF_8));
    }

    public static class Container {
        private final String body;
        private final long header_timestamp;
        private final String header_guid;

        public Container(Map<String, String> headers, String body) {
            super();
            this.header_timestamp = Long.parseLong(headers.get("timeStamp"), 10);
            this.header_guid = headers.get("guId");
            this.body = body;
        }
    }

    public static class Builder implements EventSerializer.Builder {

        @Override
        public EventSerializer build(Context context, OutputStream out) {
            FlumeEventStringBodySerializer writer = new FlumeEventStringBodySerializer(out);
            writer.configure(context);
            return writer;
        }

    }

}