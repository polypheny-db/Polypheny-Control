/*
 * Copyright 2017-2021 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.control.client;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Slf4j
public class PolyphenyControlConnector {

    private final String controlUrl;
    private static int clientId = -1;
    private final ClientType clientType;

    private final Gson gson = new Gson();


    public PolyphenyControlConnector( String controlUrl, ClientType clientType ) throws URISyntaxException {
        this.clientType = clientType;

        Unirest.config().connectTimeout( 0 );
        Unirest.config().socketTimeout( 0 );
        Unirest.config().concurrency( 200, 100 );
        this.controlUrl = "http://" + controlUrl;
        WebSocket webSocket = new WebSocket( new URI( "ws://" + controlUrl + "/socket/" ) );
        webSocket.connect();

        // Check status of connection and reconnect if necessary
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay( () -> {
            if ( webSocket.isClosed() ) {
                try {
                    webSocket.reconnectBlocking();
                } catch ( InterruptedException e ) {
                    log.error( "Interrupt while reconnecting", e );
                }
            }
        }, 1, 3L, TimeUnit.SECONDS );
    }


    public void stopPolypheny() {
        setClientType(); // Set the client type (again) - does not hurt and makes sure its set
        try {
            Unirest.post( controlUrl + "/control/stop" ).field( "clientId", clientId ).asString();
        } catch ( UnirestException e ) {
            log.error( "Error while stopping Polypheny-DB", e );
        }
    }


    public void startPolypheny() {
        setClientType(); // Set the client type (again) - does not hurt and makes sure its set
        try {
            Unirest.post( controlUrl + "/control/start" ).field( "clientId", clientId ).asString();
        } catch ( UnirestException e ) {
            log.error( "Error while starting Polypheny-DB", e );
        }
    }


    public void updatePolypheny() {
        // Check if in status idling
        String status = getStatus();
        if ( !status.equals( "idling" ) ) {
            throw new RuntimeException( "Unable to update Polypheny while it is running" );
        }
        // Trigger update
        try {
            Unirest.post( controlUrl + "/control/update" ).field( "clientId", clientId ).asString();
        } catch ( UnirestException e ) {
            log.error( "Error while updating Polypheny-DB", e );
        }
        // Wait for update to finish
        status = getStatus();
        do {
            try {
                TimeUnit.SECONDS.sleep( 1 );
            } catch ( InterruptedException e ) {
                throw new RuntimeException( "Unexpected interrupt", e );
            }
        } while ( !status.equals( "idling" ) );
    }


    public void setConfig( Map<String, String> map ) {
        JSONObject obj = new JSONObject();
        for ( Map.Entry<String, String> entry : map.entrySet() ) {
            obj.put( entry.getKey(), entry.getValue() );
        }
        try {
            Unirest.post( controlUrl + "/config/set" ).field( "clientId", clientId ).field( "config", obj.toString() ).asString();
        } catch ( UnirestException e ) {
            log.error( "Error while setting client type", e );
        }
    }


    void setClientType() {
        try {
            Unirest.post( controlUrl + "/client/type" ).field( "clientId", clientId ).field( "clientType", clientType.name() ).asString();
        } catch ( UnirestException e ) {
            log.error( "Error while setting client type", e );
        }
    }


    public String getConfig() {
        return executeGet( "/config/get" );
    }


    public String getVersion() {
        return executeGet( "/control/version" );
    }


    String getStatus() {
        return gson.fromJson( executeGet( "/control/status" ), String.class );
    }


    private String executeGet( String command ) {
        HttpResponse<String> httpResponse;
        try {
            httpResponse = Unirest.get( controlUrl + command ).asString();
            return httpResponse.getBody();
        } catch ( UnirestException e ) {
            log.error( "Exception while sending request", e );
        }
        return null;
    }


    private void executePost( String command, String data ) {
        try {
            Unirest.post( controlUrl + command ).body( data ).asString();
        } catch ( UnirestException e ) {
            log.error( "Exception while sending request", e );
        }
    }


    private class WebSocket extends WebSocketClient {

        private final Gson gson = new Gson();
        private final Logger CONTROL_MESSAGES_LOGGER = LoggerFactory.getLogger( "CONTROL_MESSAGES_LOGGER" );


        public WebSocket( URI serverUri ) {
            super( serverUri );
        }


        @Override
        public void onOpen( ServerHandshake handshakedata ) {
        }


        @Override
        public void onMessage( String message ) {
            if ( message.startsWith( "{\"version\":{" ) ) {
                return;
            }
            Type type = new TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> data = gson.fromJson( message, type );

            if ( data.containsKey( "clientId" ) ) {
                clientId = Integer.parseInt( data.get( "clientId" ) );
                setClientType();
            }
            if ( data.containsKey( "logOutput" ) ) {
                CONTROL_MESSAGES_LOGGER.info( data.get( "logOutput" ) );
            }
            if ( data.containsKey( "startOutput" ) ) {
                CONTROL_MESSAGES_LOGGER.info( data.get( "startOutput" ) );
            }
            if ( data.containsKey( "stopOutput" ) ) {
                CONTROL_MESSAGES_LOGGER.info( data.get( "stopOutput" ) );
            }
            if ( data.containsKey( "restartOutput" ) ) {
                CONTROL_MESSAGES_LOGGER.info( data.get( "restartOutput" ) );
            }
            if ( data.containsKey( "updateOutput" ) ) {
                String logStr = data.get( "updateOutput" );
                //noinspection StatementWithEmptyBody
                if ( logStr.startsWith( "Task :" ) && (logStr.endsWith( "started" ) || logStr.endsWith( "skipped" ) || logStr.endsWith( "UP-TO-DATE" ) || logStr.endsWith( "SUCCESS" )) ) {
                    // Ignore this to avoid cluttering the log. These are gradle log massage where everything is fine.
                } else {
                    CONTROL_MESSAGES_LOGGER.info( logStr );
                }
            }
        }


        @Override
        public void onClose( int code, String reason, boolean remote ) {
        }


        @Override
        public void onError( Exception ex ) {
        }

    }

}
