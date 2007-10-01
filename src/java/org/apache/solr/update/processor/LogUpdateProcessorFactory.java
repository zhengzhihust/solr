/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.update.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.DOMUtil;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryResponse;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.CommitUpdateCommand;
import org.apache.solr.update.DeleteUpdateCommand;
import org.w3c.dom.Node;

/**
 * A logging processor.  This keeps track of all commands that have passed through
 * the chain and prints them on finish();
 * 
 * If the Log level is not INFO the processor will not be created or added to the chain
 * 
 * @since solr 1.3
 */
public class LogUpdateProcessorFactory extends UpdateRequestProcessorFactory {
  
  int maxNumToLog = 8;
  
  @Override
  public void init( final SolrCore core, final Node node ) {
    if( node != null ) {
      NamedList<Object> args = DOMUtil.childNodesToNamedList( node );
      SolrParams params = SolrParams.toSolrParams( args );
      maxNumToLog = params.getInt( "maxNumToLog", maxNumToLog );
    }
  }

  @Override
  public UpdateRequestProcessor getInstance(SolrQueryRequest req, SolrQueryResponse rsp, UpdateRequestProcessor next) {
    boolean doLog = LogUpdateProcessor.log.isLoggable(Level.INFO);
    // LogUpdateProcessor.log.severe("Will Log=" + doLog);
    if( doLog ) {
      // only create the log processor if we will use it
      return new LogUpdateProcessor(req, rsp, this, next);
    }
    return null;
  }
}

class LogUpdateProcessor extends UpdateRequestProcessor {
  private final SolrQueryRequest req;
  private final SolrQueryResponse rsp;
  private final UpdateRequestProcessor next;
  private final NamedList<Object> toLog;

  int numAdds;
  int numDeletes;

  // hold on to the added list for logging and the response
  private List<String> adds;
  private List<String> deletes;

  private final int maxNumToLog;

  public LogUpdateProcessor(SolrQueryRequest req, SolrQueryResponse rsp, LogUpdateProcessorFactory factory, UpdateRequestProcessor next) {
    this.req = req;
    this.rsp = rsp;
    this.next = next;
    maxNumToLog = factory.maxNumToLog;  // TODO: make configurable
    // TODO: make log level configurable as well, or is that overkill?
    // (ryan) maybe?  I added it mostly to show that it *can* be configurable

    this.toLog = new NamedList<Object>();
  }
  
  @Override
  public void processAdd(AddUpdateCommand cmd) throws IOException {
    if (next != null) next.processAdd(cmd);

    // Add a list of added id's to the response
    if (adds == null) {
      adds = new ArrayList<String>();
      toLog.add("add",adds);
    }

    if (adds.size() < maxNumToLog) {
      adds.add(cmd.getPrintableId(req.getSchema()));
    }

    numAdds++;
  }

  @Override
  public void processDelete( DeleteUpdateCommand cmd ) throws IOException {
    if (next != null) next.processDelete(cmd);

    if (cmd.id != null) {
      if (deletes == null) {
        deletes = new ArrayList<String>();
        toLog.add("delete",deletes);
      }
      if (deletes.size() < maxNumToLog) {
        deletes.add(cmd.id);
      }
    } else {
      if (toLog.size() < maxNumToLog) {
        toLog.add("deleteByQuery", cmd.query);
      }
    }
    numDeletes++;
  }

  @Override
  public void processCommit( CommitUpdateCommand cmd ) throws IOException {
    if (next != null) next.processCommit(cmd);
    
    toLog.add(cmd.optimize ? "optimize" : "commit", "");
  }


  @Override
  public void finish() throws IOException {
    if (next != null) next.finish();
    
    // TODO: right now, update requests are logged twice...
    // this will slow down things compared to Solr 1.2
    // we should have extra log info on the SolrQueryResponse, to
    // be logged by SolrCore
    
    // if id lists were truncated, show how many more there were
    if (numAdds > maxNumToLog) {
      adds.add("...(" + (numAdds-adds.size()) + " more)");
    }
    if (numDeletes > maxNumToLog) {
      deletes.add("...(" + (numDeletes-deletes.size()) + " more)");
    }
    long elapsed = rsp.getEndTime() - req.getStartTime();
    log.info( ""+toLog + " 0 " + (elapsed) );
  }
}


