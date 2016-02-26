/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.hsd.es.geo.plugin;

import java.util.Properties;

import org.elasticsearch.action.ActionModule;
import org.elasticsearch.indices.IndicesModule;
import org.elasticsearch.plugins.Plugin;

import com.hsd.es.geo.query.GeoAdaptiveDistanceQueryParser;

public class GeoAdaptiveDistanceQueryPlugin extends Plugin {

    public static final String NAME = "geo-adaptive-distance-query";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Elasticsearch Geo-Adaptive-Distance-Query Plugin";
    }

//    public void onModule(ActionModule actionModule) {
//    	registerQueryParser(RangeJoinQueryParser.class);
//    }

    public void onModule(IndicesModule indicesModule) {
    	indicesModule.registerQueryParser(GeoAdaptiveDistanceQueryParser.class);
    }

}