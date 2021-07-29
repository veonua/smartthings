/**
 *  
 *
 *  Updated March 2020 by Melinda Little to fix and expand functions.  Orignal code by Steve The Geek with updates from others.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

metadata {
	definition (name: "Nanoleaf Aurora Smarter API", namespace: "SteveTheGeekAH", author: "Veon. Steve The Geek, Melinda Little" /*,
    		mnmn:"SmartThings", vid: "generic-rgbw-color-bulb", ocfDeviceType: 'oic.d.light' */) {
 		capability "Actuator"
		capability "Light"
		capability "Switch Level"
		capability "Switch"
		capability "Color Control"
       	capability "Polling"
       	capability "Refresh"
        capability "Media Presets"
        //capability "Tv Channel"
        capability "Media Track Control"
		capability "Health Check"

		command "previousScene"
		command "nextScene"
		command "changeScene" //for CoRE access to scenes
		command "setScene1"
		command "setScene2"
		command "setScene3"	
        command "requestAPIkey"
        command "clearApiKey"
        command "setPanelColor"
        command "playPreset"
        command "previousTrack"
		command "nextTrack"
        
        //command "setTvChannel"
        //command "setTvChannelName"
        command "channelUp"
		command "channelDown"
		
            
		attribute "scene", "string"
        attribute "scenesList", "string"
        attribute "IPinfo", "string"
        attribute "retrievedAPIkey", "string"
        attribute "apiKeyStatus", "string"
        attribute "clearKey", "string"
        attribute "panelIds", "string"
	}

	simulator {

	}
    
	tiles {

		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, decoration: "flat", canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: 'on', action: "off", icon: "http://stevethegeek.net/smartthings/aurora/aurora-on.png", backgroundColor: "#00a0dc"
				attributeState "off", label: 'off', action: "on", icon: "http://stevethegeek.net/smartthings/aurora/aurora-off.png", backgroundColor: "#ffffff"
		}
            	
		tileAttribute ("level", key: "SLIDER_CONTROL", range:"(1..100)") {
                	attributeState "level", action:"setLevel"
		}
        
		tileAttribute ("color", key: "COLOR_CONTROL") {
                	attributeState "color", action:"setColor"
		}
	}

		standardTile("scene1", "scene1", width: 2, height: 1, decoration: "flat") {
        		state "val", label: '${currentValue}', backgroundColor: "#ffffff", action: "setScene1" 
        	}
        
        	standardTile("scene2", "scene2", width: 2, height: 1, decoration: "flat") {
        		state "val", label: '${currentValue}', backgroundColor: "#ffffff", action: "setScene2" 
		}
		
        	standardTile("scene3", "scene3", width: 2, height: 1, decoration: "flat") {
        		state "val", label: '${currentValue}', backgroundColor: "#ffffff", action: "setScene3" 
        	}
		
		standardTile("previousScene", "scene", width: 1, height: 1, decoration: "flat") {
			state "default", label: "", backgroundColor: "#ffffff", action: "previousScene", icon: "http://stevethegeek.net/smartthings/aurora/aurora-left.png"
		} 

		standardTile("nextScene", "scene", width: 1, height: 1, decoration: "flat") {
			state "default", label: "", backgroundColor: "#ffffff", action: "nextScene", icon: "http://stevethegeek.net/smartthings/aurora/aurora-right.png"
		} 
        
        valueTile("IPdisplay", "device.IPinfo", width: 4, height: 1, decoration: "flat") {
			state "IPinfo", label: '${currentValue}', backgroundColor: "#ffffff"
		}

		standardTile("key", "device.retrievedAPIkey", decoration: "flat", width: 1, height: 1) {
       		state "default", action:"requestAPIkey", label: "Get API Key"
        }

		standardTile("clearKey", "device.clearKey", decoration: "flat", width: 1, height: 1) {
       		state "default", action:"clearApiKey", label: "Clear  API Key"
        }

        valueTile("keyStatus", "device.apiKeyStatus", width: 4, height: 1, decoration: "flat") {
			state "apiKeyStatus", label: '${currentValue}', backgroundColor: "#ffffff"
		}
        
        standardTile("refresh", "device.switch", decoration: "flat", width: 1, height: 1) {
       		state "default", action:"refresh", icon:"st.secondary.refresh"
        } 
                
		main "switch"
			details(["switch","scene1","scene2","scene3","previousScene","currentScene","nextScene","IPdisplay","refresh","keyStatus","key","clearKey"])
	}

    	preferences {
        	input name: "apiKey", type: "text", title: "Aurora API Key", description: "Enter The Key Returned By The Api Authentication Method", required: true
			input name: "scene1", type: "text", title: "Favorite Scene 1", description: "Enter a Scene name", required: false
        	input name: "scene2", type: "text", title: "Favorite Scene 2", description: "Enter a Scene name", required: false
        	input name: "scene3", type: "text", title: "Favorite Scene 3", description: "Enter a Scene name", required: false  
    		input name: "inputIP", type: "text", title: "Local IP Address", description: "Enter the IP address without the port number", required: false            
	}
}

def installed() {
	initialize()
	setTimer()

}

def updated() {
	log.debug "UPDATED"
    unschedule()
	initialize()
    setTimer()

}

def initialize() {
	
    sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
	sendEvent(name: "healthStatus", value: "online")
	sendEvent(name: "DeviceWatch-Enroll", value: "{\"protocol\": \"LAN\", \"scheme\":\"untracked\", \"hubHardwareId\": \"${device.hub.hardwareID}\"}", displayed: false)
    
	def displayText
    if (inputIP) {
    	def hexID = setNetworkID()
		displayText = "${device.name} at ${inputIP}:16021\n${hexID}"
    }
    else {
    	displayText = "No IP Address provided"
    }
	sendEvent(name: "IPinfo", value: displayText)
//	log.debug "DISPLAY - ${displayText} ${device.currentValue("IPinfo")}"

	setKeyStatus()

}

def ping() {

	refresh()
	return 1
}

def parse(String description) {

    	def message = parseLanMessage(description)
//		log.debug "IN PARSE ${message}"
    	if(message.json) {
        
        	sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
		    sendEvent(name: "healthStatus", value: "online")
            
      		def auroraOn = message.json.state.on.value
      
      		if(auroraOn && device.currentValue("switch") == "off") {
        		log.debug("Aurora has been switched on outside of Smartthings")
      			sendEvent(name: "switch", value: "on", isStateChange: true)
      		}
      		
		if(!auroraOn && device.currentValue("switch") == "on") {
        		log.debug("Aurora has been switched off outside of Smartthings")
      	 		sendEvent(name: "switch", value: "off", isStateChange: true)
      		}
      
      	def currentScene = message.json.effects.select
      		if(currentScene != device.currentValue("scene")) {
         	log.debug("Scene was changed outside of Smartthings")
         	sendEvent(name: "scene", value: currentScene, isStateChange: true)
            sendEvent(name: "tvChannel", value: currentScene, isStateChange: true)
            // sendEvent(name: "tvChannelName", value: currentScene, isStateChange: true)
      	}

      	def currentBrightness = message.json.state.brightness.value
      	def deviceBrightness = "${device.currentValue("level")}"
      	if(currentBrightness != device.currentValue("level")) {
         	log.debug("Brightness was changed outside of Smartthings")
         	sendEvent(name: "level", value: currentBrightness, isStateChange: true)
      	}
      
      	def effectsList = message.json.effects.effectsList ?: []
   		if(effectsList.toString() != device.currentValue("scenesList").toString()) {
         	log.debug("List of effects was changed in the Aurora App")
         	sendEvent(name: "scenesList", value: effectsList, isStateChange: true)
      	}
        def favsList = []
	    if (effectsList.size() > 0) {
    		for (int i = 0; i < effectsList.size();i++) {
	    		favsList[i] = "{\"id\":\"${i}\",\"name\":\"${effectsList[i]}\"}"
    		}
      	}
        
        sendEvent(name: "supportedTrackControlCommands", value: ['previousTrack', 'nextTrack'], display: false)
		sendEvent(name: "presets", value: favsList, display: true)

//  Save panelid Info
		sendEvent(name: "panelIds", value: message.json.panelLayout.layout.positionData.panelId.join(","), display: false)        
//		log.debug message.json.panelLayout.layout.positionData.panelId


    	} else {
      		log.debug("Response from PUT, refreshing")
            refresh()
    	}
}

def setTimer() {
	log.debug "TIMER SET"
	runEvery5Minutes(refresh)

}

def refresh() {
//	log.debug "REFRESH"
	createGetRequest("");
}

def off() {
	sendEvent(name: "switch", value: "off", isStateChange: true)
	createPutRequest("state", "{\"on\" : { \"value\" : false}}")
} 

def on() {
	sendEvent(name: "switch", value: "on", isStateChange: true)
	createPutRequest("state", "{\"on\" : { \"value\" : true}}")
}

def previousScene() {
	log.debug("previousScene")
  	def sceneListString = device.currentValue("scenesList").replaceAll(", ", ",")
  	def sceneList = sceneListString.substring(1, sceneListString.length()-1).tokenize(',')
  	def currentSelectedScene = device.currentValue("scene");
  	def index = sceneList.indexOf(currentSelectedScene)
    	log.debug(index)
  
  	if(index == -1) {
    		index = 1;
  	}
  	
	index--
  	if(index == -1) {
     		index = sceneList.size -1
  	}
	
	changeScene(sceneList[index])
}

def nextScene() {
	log.debug("nextScene")
  	def sceneListString = device.currentValue("scenesList").replaceAll(", ", ",")
  	def sceneList = sceneListString.substring(1, sceneListString.length()-1).tokenize(',')
  	def currentSelectedScene = device.currentValue("scene");
  	def index = sceneList.indexOf(currentSelectedScene)
  
  	index++
    	if(index == sceneList.size) {
     		index = 0
  	}
  	
	changeScene(sceneList[index])
}

def changeScene(String scene) {

   	sendEvent(name: "scene", value: scene, isStateChange: true)
    sendEvent(name: "tvChannel", value: scene, isStateChange: true)
    //sendEvent(name: "tvChannelName", value: scene, isStateChange: true)
    
    sendEvent(name: "trackData", value: "{\"station\": \"${scene}\"}", isStateChange: true)
    sendEvent(name: "trackDescription", value: scene, isStateChange: true)
    
    if(device.currentValue("switch") == "off") { sendEvent(name: "switch", value: "on") }
   	createPutRequest("effects", "{\"select\" : \"${scene}\"}")
}

// capability: Tv Channel
def setTvChannel(channel) {
    log.debug "setTvChannel($channel)"
    sendEvent(name: "tvChannel", value: channel)
}

def channelUp() {
    log.debug "channelUp()"
    //chup()
    sendEvent(name: "tvChannel", value: "MBC") // TODO
}

def channelDown() {
    log.debug "channelDown()"
    //chdown()
    sendEvent(name: "tvChannel", value: "KBS") // TODO
}

def previousTrack() {
	previousScene()
}

def nextTrack() {
	nextScene()
}

//def setTvChannelName(tvChannelName) {
//	log.debug("setTvChannelName")
//	changeScene(tvChannelName)
//}

//def setTvChannel(channel) {
//	log.debug("setTvChannel")
//    changeScene(channel)
//}


def setScene1() {
	sendEvent(name: "scene1", value: "${scene1}")
   	changeScene("${scene1}")
}    

def setScene2() {
	sendEvent(name: "scene2", value: "${scene2}")
   	changeScene("${scene2}")
} 

def setScene3() {
	sendEvent(name: "scene3", value: "${scene3}")
	changeScene("${scene3}")
} 

def setLevel(Integer value) {
	log.debug "LEVEL ${value}"
    sendEvent(name: "level", value: value, isStateChange: true)
    if(device.currentValue("switch") == "off") { sendEvent(name: "switch", value: "on") }
	createPutRequest("state", "{\"brightness\" : {\"value\" : ${value}}}")
}

def setColor(value) {
   	sendEvent(name: "scene", value: "--", isStateChange: true)
    sendEvent(name: "tvChannel", value: "--", isStateChange: true)
    sendEvent(name: "tvChannelName", value: "--", isStateChange: true)
    
   	sendEvent(name: "color", value: value.hex, isStateChange: true)
    if (device.currentValue("switch") == "off") 
    	{ sendEvent(name: "switch", value: "on") }
   	//createPutRequest("state", "{\"hue\" : {\"value\": ${(value.hue*360/100).toInteger()}}, \"sat\" : {\"value\": ${value.saturation.toInteger()}}}")
    createPutRequest("effects", createAnimatedColor(value))
    sendEvent(name: "hue", value: value.hue, isStateChange: true)
    sendEvent(name: "saturation", value: value.saturation, isStateChange: true)
}

def setPanelColor(panel, toColor, blink=false) {
	def RBG = codeColor(toColor)
    log.debug RBG
    if (RBG?.red !=null) {
    	def numFrames = (blink) ? 2 : 1 
    	def loopVal = (blink) ? "true" : "false" 
    	def animType = (blink) ? "custom" : "static"
        def firstFrame = "${RBG.red.toInteger()} ${RBG.green.toInteger()} ${RBG.blue.toInteger()} 0 15"
    	def secondFrame = (blink) ? " 0 0 0 0 5" : "" 
        def animData = "1 ${panel} ${numFrames} ${firstFrame}${secondFrame}"
//        log.debug animData
        def jsonCmd = "{\"write\": {\"command\":\"display\",\"animType\":\"${animType}\",\"animData\":\"${animData}\",\"loop\":${loopVal}}}"
//        log.debug jsonCmd
        createPutRequest("effects", jsonCmd)
    }
    else {log.error "Bad Panel Color Received : ${toColor}"}
}

def codeColor(toColor) {
    def RBG =[:]
    if (toColor.startsWith("#")) {
    	if (toColor.length() == 7) {
    		RBG.red = convertHexToInt(toColor[1..2])
    		RBG.green = convertHexToInt(toColor[3..4])    
       		RBG.blue = convertHexToInt(toColor[5..6])
        }
        else {
        	log.error "Bad Panel Color Received : ${toColor}. Defaulted to White"
            RBG.red = "255"
        	RBG.green = "255"
            RBG.blue = "255"
        }
    } 
    else if (toColor.equalsIgnoreCase("Red")) {
        	RBG.red = "255"
        	RBG.green = "0"
            RBG.blue = "0"
        }
    else if (toColor.equalsIgnoreCase("Blue")) {
        	RBG.red = "0"
        	RBG.green = "0"
            RBG.blue = "255"
        }
    else if (toColor.equalsIgnoreCase("Green")) {
        	RBG.red = "0"
        	RBG.green = "255"
            RBG.blue = "0"
        }
    else if (toColor.equalsIgnoreCase("Yellow")) {
        	RBG.red = "255"
        	RBG.green = "255"
            RBG.blue = "0"
        }
    else if (toColor.equalsIgnoreCase("Black")) {
        	RBG.red = "0"
        	RBG.green = "0"
            RBG.blue = "0"
        } 
    else if (toColor.equalsIgnoreCase("Orange")) {
        	RBG.red = "255"
        	RBG.green = "140"
            RBG.blue = "0"
        }   
    else if (toColor.equalsIgnoreCase("Pink")) {
        	RBG.red = "255"
        	RBG.green = "105"
            RBG.blue = "180"
        }
    else if (toColor.equalsIgnoreCase("Purple")) {
        	RBG.red = "128"
        	RBG.green = "0"
            RBG.blue = "128"
        }    
    else {  // Color input is White or invalid and thus defaults to white
        	RBG.red = "255"
        	RBG.green = "255"
            RBG.blue = "255"
        }

    
    log.debug RBG
    return RBG
}

def playPreset(presetId) {
 	log.debug "PRESET ${presetId}"

    def favsList = new groovy.json.JsonSlurper().parseText(device.currentValue("presets"))
    changeScene("${favsList.name[presetId.toInteger()]}")

}

// gets the address of the hub
private getCallBackAddress() {
    	return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

private createPutRequest(String url, String body) {
    def setKey = setAPIkey()
	log.debug("/api/v1/${setKey}/${url}")
   	log.debug("body : ${body}")
    
   	def result = new physicalgraph.device.HubAction(
        	method: "PUT",
        	path: "/api/v1/${setKey}/${url}",
        	body: body,
        	headers: [
        	HOST: getHostAddress()
     		]
        )
	sendHubCommand(result)
//    return result;
}

private createGetRequest(String url) {

    def setKey = setAPIkey()
	//log.debug("/api/v1/${setKey}/${url}")
//    log.debug "GET KEY : ${setKey}"
    
   	def result = new physicalgraph.device.HubAction(
            	method: "GET",
            	path: "/api/v1/${setKey}/${url}",
            	headers: [
                HOST: getHostAddress()
            	]
	    )
	sendHubCommand(result)
//        return result;
}

private requestAPIkey() {
    
   	def result = new physicalgraph.device.HubAction(
            	method: "POST",
            	path: "/api/v1/new",
            	headers: [
                HOST: getHostAddress()
            	], null, [callback: "${receiveAPIkey}"]
        )
	sendHubCommand(result)

}

def receiveAPIkey(message) {

	log.debug "MESSAGE : ${message}"
	if(message.json) {
//    	log.debug "JSON ${message.json}"
		def receivedKey = message.json.auth_token
        log.debug "KEY ${receivedKey}"
       	sendEvent(name: "retrievedAPIkey", value: "${receivedKey}")
       	sendEvent(name: "apiKeyStatus", value: "API Key Retrieved")
    }
    else {
	    log.debug "API Request Failed"
        setKeyStatus()
    }

}

def setKeyStatus() {

    def keyStatus
    def health
	if (device.currentValue("retrievedAPIkey")) {
    	keyStatus = "API Key Retrieved"
    }
    else if (apiKey) {
    	keyStatus = "API Key Was Entered"
    } 
    else {
    	keyStatus = "No API Key Found"    
	}
	sendEvent(name: "apiKeyStatus", value: keyStatus)

}

def setAPIkey() {
	def storedKey1 = "fvTBDLvOXYJ2UR6mQz7P6oSqYfLrmViU" // device.currentValue("retrievedAPIkey")
    def storedKey2 = "Qthjlg9w7lDCVkAnOfNpcqeWRC1Vq0LD"
    //def setKey = storedKey ?: apiKey
    return storedKey1
}

def clearApiKey() {

   	sendEvent(name: "retrievedAPIkey", value: "")
    setKeyStatus()
}

// gets the address of the device
private getHostAddress() {
    	def ip = inputIP
    	def port = "16021"
    	if (!ip || !port) {
        	def parts = device.deviceNetworkId.split(":")
        	if (parts.length == 2) {
            		ip = convertHexToIP(parts[0])
            		port = convertHexToInt(parts[1])
        	} else {
            		log.warn "Can't figure out ip and port for device: ${device.name}"
        	}
		}

    	log.debug "Using IP: $ip and port: $port for device: ${device.name}"
    	return ip + ":" + port
}

private Integer convertHexToInt(hex) {
    	return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    	return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

def setNetworkID() {

    def deviceHexID = "${convertIPtoHex(inputIP)}:${convertPortToHex("16021")}"
    device.deviceNetworkId = deviceHexID
	return deviceHexID
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
//    log.debug "IP address entered is $ipAddress and the converted hex code is $hex"
    return hex
}

private String convertPortToHex(port) {
    String hexport = port.toString().format( '%04X', port.toInteger() )
//    log.debug hexport
    return hexport
}


private String createAnimatedColor(value) {
	def hue = (value.hue*360/100).toInteger()
    def sat = value.saturation.toInteger()
    def palette = [
    [
      hue: hue,
      saturation: sat,
      brightness: 60
    ],
    [
      hue: hue,
      saturation: sat,
      brightness: 80
    ],
    [
      hue: hue,
      saturation: sat,
      brightness: 100
    ]
  	]
    
    def transTime = [
            minValue: 0,
            maxValue: 20
        ]
    def delayTime = [
            minValue: 0,
            maxValue: 3
        ]
                        
	return JsonOutput.toJson(write:[
    	command: "display",
  		version: "2.0",
  		animType: "random",
  		colorType: "HSB",
        transTime: transTime,
        //delayTime: delayTime,
  		palette: palette    
        ]
    )
}