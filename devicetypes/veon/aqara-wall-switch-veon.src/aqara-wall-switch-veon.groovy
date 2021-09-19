// This is the truncated version of 
// https://github.com/aonghus-mor/SmartThingsPublic/blob/master/devicetypes/aonghus-mor/testcode.src/testcode.groovy

import groovy.json.JsonOutput
import physicalgraph.zigbee.zcl.DataType

metadata {
    definition (name: "Aqara Wall Switch (veon)", namespace: "veon", author: "Veon", 
        //vid: "1ce7101f-7613-3865-811a-196227c7e4ec", // momentary only with temperature & battery (probably view of tile) 
        //ocfDeviceType: "oic.d.switch",
        ocfDeviceType: "x.com.st.d.remotecontroller", mcdSync: true) {
        capability "Actuator"
		capability "Configuration"
		capability "Refresh"
		capability "Health Check"
		capability "Switch"
		capability "Power Meter"
		capability "Button"
        capability "HoldableButton"
		capability "Temperature Measurement"
        capability "Health Check"
        
        command "childOn", ["string"]
		command "childOff", ["string"]
        command "childToggle", ["string"]
        
        command "childRefresh"
        command "recreateChildDevices"
        command "deleteChildren"

		// QBKG04LM: one button, no neutral required
		fingerprint profileId: "0104", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
        		manufacturer: "LUMI", model: "lumi.ctrl_neutral1", deviceJoinName: "Aqara Wall switch"
        // QBKG03LM: two buttons, no neutral required
		fingerprint profileId: "0104", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
        		manufacturer: "LUMI", model: "lumi.ctrl_neutral2", deviceJoinName: "Aqara Wall switch"
        // QBKG23LM: one button, neutral is required
        // Basic {"simple":"01 0104 0051 01|09 0000 0004 0003 0006 0010 0005 000A 0001 0002 02 0019 000A","application":"3D","manufacturer":"LUMI","model":"lumi.switch.b2nacn02"},
        // ??    {"simple":"02 0104 0051 01|02 0006 0010 00","application":"","manufacturer":"","model":""},
        // ??    {"simple":"03 0104 0009 01|01 000C 02 000C 0004","application":"","manufacturer":"","model":""},
        // ??    {"simple":"04 0104 0053 01|01 000C 01 000C","application":"","manufacturer":"","model":""}
        fingerprint profileId: "0104", inClusters: "0000,0004,0003,0006,0010,0005,000A,0001,0002", outClusters: "000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b1nacn02", deviceJoinName: "Aqara Switch QBKG23LM"
        // QBKG24LM: two buttons, neutral is required
        fingerprint profileId: "0104", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b2nacn02", deviceJoinName: "Aqara Switch QBKG24LM"
        
        // Aqara H1 one button, no neutral required
        // Basic       {"simple":"01 0104 0100 01|07 0000 0002 0003 0004 0005 0006 0009 02 000A 0019","application":"0B","manufacturer":"LUMI","model":"lumi.switch.l1aeu1"},
        // GreenPower  {"simple":"F2 A1E0 0061 00 00 01 0021","application":null,"manufacturer":null,"model":null}
        fingerprint profileId: "0104", inClusters: "0000,0002,0003,0004,0005,0006,0009", outClusters: "000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.l1aeu1", deviceJoinName: "Aqara H1 EU"
    }
    
    tiles {
		standardTile("button", "device.button", width: 2, height: 2) {
			state "default", label: "", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
			state "button 1 pushed", label: "pushed #1", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#00A0DC"
		}

		main (["button"])
		details(["button"])
	}

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "leftButtonDisconnect", type: "bool", title: "Disconnect left button from switch", defaultValue: false
        input name: "rightButtonDisconnect", type: "bool", title: "Disconnect right button from switch (double button devices)", defaultValue: false
    }
}

private getCLUSTER_POWER() { 0x0001 }
private getCLUSTER_TEMPERATURE() { 0x0002 }
private getCLUSTER_GROUPS() { 0x0004 }
private getCLUSTER_SCENES() { 0x0005 }
private getCLUSTER_AQARA()  { 0xFCC0 }
private getCLUSTER_MULTISTATE()  { 0x0012 }
private getBINDING_TABLE_RESPONSE() {0x8033}
private getBIND_RESPONSE() {0x8021}

def getChildSwitches() {
	return getChildDevices()
}

def parse(String description) {
	def handled = false
    
    if (description.startsWith("catchall")) {
    	return parseCatchAllMessage(description)
    }
    
    log.trace (">${description}")
    def events = []
	
    if (description.startsWith("on/off")) {
    	def rrr = parseCustomMessage(description)
        log.trace ("<${rrr}")
    	return rrr
    }
    
    def descMap = zigbee.parseDescriptionAsMap(description)
    def cluster = Integer.parseInt(descMap.cluster, 16)
    def attrId = Integer.parseInt(descMap.attrId, 16)
	def value = Integer.parseInt(descMap.value, 16)
    def endpoint = Integer.parseInt(descMap.endpoint, 16)
    
    switch (cluster) {
    	case zigbee.BASIC_CLUSTER: // Basic
            if (attrId == 0x7) {
            	events = setPowerSource(value)
            } else if (attrId >= 0xff22) {
                events = updateDecoupleStatus(attrId-0xff22, value)
            } else {
            	log.warn("unhandled attrId: $attrId")
            }
            handled = events!=[]
            break
        case CLUSTER_POWER: // voltage
        	handled = true
        	break;
        case CLUSTER_TEMPERATURE: // Device Temperature Configuration
        	if ( attrId != 0x0 ) break
            events = setTemp( value )
            handled = true
            break
        case zigbee.ONOFF_CLUSTER:
        	events = parseOnOff(descMap)
            handled = true
            break
        case CLUSTER_MULTISTATE: // Multistate Input 
            switch (attrId) {
            	case 0x0055: // PresentValue
                	events = parseOnOff(descMap)
            	case 0x006F: // StatusFlags
                	handled = true
                    break
            }
            break    
        case 0x000C: //analog input
        	if ( attrId != 0x0055 ) break
            
            float val = Float.intBitsToFloat(Integer.parseInt(descMap.value, 16))
            events = getWatts(val)
        	handled = true
        	break
        default:
        	log.info("Unknown cluster:$cluster: $description ::: $descMap")
    }
    
    if (!handled) {
    	log.warn("unhandled message: $description")
    }
	
    def dat = new Date()
    def now = dat.format("HH:mm:ss EEE dd MMM '('zzz')'", location.timeZone) + "\n" + state.lastPressType
    events << createEvent(name: "lastCheckin", value: now, descriptionText: "Check-In", displayed: debugLogging)
    
    return events
}

def parseOnOff(descMap) {
	//log.debug("parseOnOff") 
        
    def endpoint = Integer.parseInt(descMap.endpoint, 16)
	def value = Integer.parseInt(descMap.value, 16)
    def events = []
    
    def switchIndex = state.switchEP.indexOf(endpoint) + 1 // Buttons starts with #1
    def buttonIndex = state.buttonEP.indexOf(endpoint) + 1 
    
	if (switchIndex > 0) {
    	log.info("Switch#$switchIndex v:$value (Endpoint:$endpoint )")
        
        def switchState = value == 1 ? "on" : "off"
        def descriptionText = "Switch has been turned $switchState"

        if (switchIndex == 1) {
            events << createEvent(name:"switch", value:switchState, descriptionText: descriptionText)
        } else {
        	childDevices.find { it.deviceNetworkId.endsWith(":${endpoint}") }.
                sendEvent(name:"switch", value:switchState, descriptionText: descriptionText)
		}
    } else if (buttonIndex > 0) {
        def buttonState = valueToClickType(value)

        log.info("Button#$buttonIndex v:$value click:$buttonState (Endpoint:$endpoint)")        
        events << createEvent(name: 'button', value: buttonState, data:[buttonNumber: buttonIndex ], isStateChange: true, displayed: false)
        
        if (buttonIndex > state.numberOfSwitches) { 
        	log.info("All the buttons are pushed")
            for (int i = 1; i <= state.numberOfSwitches; i++) {
        		events << createEvent(name: 'button', value: buttonState, data:[buttonNumber: i ], isStateChange: true, displayed: false)
                sendButtonEvent(state.buttonEP[i-1], buttonState)
            }
        } else {
            // Create and send component event
            sendButtonEvent(endpoint, buttonState)
        }
	} else {
		log.warn("parseOnOff ( endpoint: $endpoint, attrId: $descMap.attrId, value: $descMap.value )")
    }
    return events
}

private channelNumber(String dni) {
	dni.split(":")[-1] as Integer
}

private sendButtonEvent(buttonEP, buttonState) {
	if (buttonEP == state.buttonEP[0]) return // first button handled by parent
	def child = childDevices?.find { channelNumber(it.deviceNetworkId) == buttonEP }

	if (child) {
		def descriptionText = "$child.displayName was $buttonState" // TODO: Verify if this is needed, and if capability template already has it handled
		child?.sendEvent([name: "button", value: buttonState, data: [buttonNumber: 1], descriptionText: descriptionText, isStateChange: true])
	} else {
		log.warn "Child device $buttonEP not found!"
	}
}

private getButtonLabel(buttonNum) {
	return [
		"left button", 
		"right button",
        "both buttons",
		"middle button",
	][buttonNum-1]
}

private def valueToClickType(int value) {
	// QBKG03LM QBKG04LM - {0: pushed}
    // QBKG23LM QBKG24LM - {1: pushed, 2: double}
    def clickTypes = ['pushed', 'pushed', 'double']
    return clickTypes[value]
}

private def parseCustomMessage(String description) 
{
	// H1 posts 'on/off: {0|1}' for normal presses
    //
	// QBKG03LM QBKG04LM 
    // posts 'on/off: {0|1}' when any button is held
	if (!description?.startsWith('on/off: ')) {
    	log.error("Unsupported '$description'")
        return []
    }
    
    def value = (description[-1] == '0') ? 'held' : ''
    log.info("We don't know which button $value, send event to button#1")
    return [createEvent(name: 'button', value: value, data:[buttonNumber: 1], isStateChange: true)]
}

def updateDecoupleStatus(int button, int value) {
    // Disconnect left  button from relay: write uint8 (0x20) value (connected: 0x12, disconnected: 0xFE) to attribute 0xFF22 of endpoint 0x01, cluster 0x0000
	// Disconnect right button from relay: write uint8 (0x20) value (connected: 0x22, disconnected: 0xFE) to attribute 0xFF22 of endpoint 0x01, cluster 0x0000
	log.info("Update decouple switch $button = ${Integer.toHexString(value)}")
    
    def decoupled = value == 0xFE
    
    def setting = ""
    switch (button) {
    	case 0:
        	state.decoupled1 = decoupled
        	settings.leftButtonDisconnect = decoupled
        		break
        case 1:
        	state.decoupled2 = decoupled
        	settings.rightButtonDisconnect = decoupled
        		break
        default:
        	log.warn ("unknown button ${button} ${value}")
            return
    }
   return createEvent(name:"button"+button+"Decoupled", value: decoupled) 
}

private def setTemp(int temp)
{ 
    temp = temp ? temp : 0
    if ( state.tempNow == temp ) return []
    
    state.tempNow = temp
    state.tempOffset = tempOffset ? tempOffset : -2
    if ( getTemperatureScale() != "C" ) 
    temp = celsiusToFahrenheit(temp)
    state.tempNow2 = temp + state.tempOffset     
    displayDebugLog("Temperature is now ${state.tempNow2}°")
    return createEvent(name: "temperature", value: state.tempNow2, unit: getTemperatureScale())
}

private def setPowerSource(int value) {
	def batteryPresent = value != 3
	return createEvent(name: "powerSource", value: value)
}

private def getWatts(Float pwr)
{
	def event = []
    pwr = pwr ? pwr : 0.0f
    def old = state.power? state.power : 0.0f
    if ( Math.abs( pwr - old ) > 1e-4 )
    {	
    	state.power = pwr
    	event << createEvent(name: 'power', value: pwr, unit: 'W')
    }
    displayDebugLog("Power: ${pwr} W")
	return event
}

private def getkWh(Float val) 
{
	log.debug("kWh ${val}")
}

private def getBattery(Float val) 
{
	//log.debug("battery ${val}") // 3300.0 ??
}


def childOn(String deviceId) {
    updateLocalSwitchState(endpointNumber(deviceId), 0x01)
}

def childOff(String deviceId) {
    updateLocalSwitchState(endpointNumber(deviceId), 0x00)
}

def childToggle(String dni) 
{
 	updateLocalSwitchState(endpointNumber(deviceId), 0x02) 
}

def on() 
{
    updateLocalSwitchState(state.switchEP[0], 0x01)
}

def off() 
{
    updateLocalSwitchState(state.switchEP[0], 0x00)
}

def updateLocalSwitchState(int endpointId, int on) {
    return zigbee.command(zigbee.ONOFF_CLUSTER, on, "", [destEndpoint: endpointId] )
}

def ping()
{
	displayDebugLog("ping")
    return refresh()
}

def refresh() {
	log.info "refresh..."
    def cmds = zigbee.onOffRefresh() //zigbee.readAttribute(zigbee.ONOFF_CLUSTER, 0, [destEndpoint: state.switchEP[0]] ) // refresh switch state
    
    getChildSwitches()?.each {
        cmds += zigbee.readAttribute (zigbee.ONOFF_CLUSTER, 0, [destEndpoint: endpointNumber(it.deviceNetworkId)])
    }
    cmds += zigbee.readAttribute(CLUSTER_TEMPERATURE, 0x0000, [destEndpoint: 0x01])		// temperature 
    
    if (!isL1aEU1()) {
    	cmds += zigbee.readAttribute(CLUSTER_TEMPERATURE, 0x0000, [destEndpoint: 0x01]) +		// voltage
				zigbee.readAttribute(0x000C, 0x0055) + // power consumption
                zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0xFF22, [mfgCode: "0x115F"]) // disconnected buttons
    	if (state.numberOfSwitches>1)
          		cmds += zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0xFF23, [mfgCode: "0x115F"])
    } else {
    	cmds += zigbee.readAttribute(CLUSTER_MULTISTATE, 0x0055) + // PresentValue
            	zigbee.readAttribute(CLUSTER_MULTISTATE, 0x006F)  // StatusFlags
                //zigbee.readAttribute(CLUSTER_AQARA, 0x00EE) +
                //zigbee.readAttribute(CLUSTER_AQARA, 0x00F7) +
                //zigbee.readAttribute(CLUSTER_AQARA, 0x00FC)
                //zigbee.readAttribute(CLUSTER_MULTISTATE, 0x004A)   // NumberOfStates = 6
    }
    
    sendEvent( name: 'checkInterval', value: 3000, data: [ protocol: 'zigbee', hubHardwareId: device.hub.hardwareID ] )

	return cmds
}


def configure() {
    initEndpoints() // reinit endpoints for debug
    // zigbee.electricMeasurementPowerConfig() +
    def binding = zigbee.onOffConfig()
    
    /*
    getChildSwitches()?.each {
    	binding += zigbee.configureReporting(zigbee.ONOFF_CLUSTER, 0, DataType.UINT16, 0, 599, null, [destEndpoint: endpointNumber(it.deviceNetworkId)])
    }
    */
    def cmds = zigbee.configureReporting(CLUSTER_MULTISTATE,  0x0055, DataType.UINT16, 0, 600, null) + //PresentValue
               zigbee.configureReporting(CLUSTER_MULTISTATE,  0x006F, DataType.BITMAP8, 0, 600, null) //StatusFlags
               //zigbee.configureReporting(CLUSTER_TEMPERATURE, 0x0000, DataType.INT16, 0, 600, null) // temp
    
    if (!isL1aEU1()) {
    	cmds += zigbee.writeAttribute(zigbee.BASIC_CLUSTER, 0xFF22, DataType.UINT8, leftButtonDisconnect  ? 0xFE : 0x12, [mfgCode: "0x115F"]) + // disconnected buttons
        		zigbee.writeAttribute(zigbee.BASIC_CLUSTER, 0xFF23, DataType.UINT8, rightButtonDisconnect ? 0xFE : 0x22, [mfgCode: "0x115F"])
    }
     
    //+ readDeviceBindingTable() // Need to read the binding table to see what group it's using
    def res = binding + cmds + refresh()
	log.debug("configure ${res}")
    res
}


def installed() {
	log.info "installed..."
    
    updateDataValue("onOff", "catchall")
    initEndpoints()
    createChildDevices()
    def numButtons = numberOfSwitches();  
    sendEvent(name: 'supportedButtonValues', value: state.supportedButtonValues.encodeAsJSON(), displayed: false)
	sendEvent(name: 'numberOfButtons', value: numButtons, displayed: false)
	numButtons.times {
		sendEvent(name: "button", value: "pushed", data: [buttonNumber: it+1], displayed: false)
	}
    createChildButtonDevices(numButtons)
	configure()
}

def updated() {
    log.info "updated..."
    updateDataValue("onOff", "catchall")
    response(configure())
}

def recreateChildDevices() {
    log.debug "recreateChildDevices"
    deleteChildren()
    createChildDevices()
}

def deleteChildren() {
	log.debug "deleteChildren"
	def children = getChildDevices()
    
    children.each {child->
  		deleteChildDevice(child.deviceNetworkId)
    }
}

private void createChildDevices() {
	def numberOfSwitches = numberOfSwitches()
    
    if (childDevices || numberOfSwitches<2) {
    	return
    }
    
    displayDebugLog("createChildDevices $numberOfSwitches")
    for (i in 2..numberOfSwitches) {
        String childDni = "$device.zigbeeId:${state.switchEP[i-1]}"
        String componentLabel = "$device.displayName$i"

		displayDebugLog(childDni)
        def child = addChildDevice("Smartthings", "Child Switch Health", childDni, device.hubId,
				[label         : "${device.displayName} Switch $i",
				 isComponent   : false, // "false"
                 //componentName : "switch$i", componentLabel: "Switch $i"
				 //completedSetup: true, 
				])
		child.sendEvent(name: "switch", value: "off")
    }
}


private void createChildButtonDevices(numberOfButtons) {
	state.oldLabel = device.label

	log.debug "Creating $numberOfButtons children"

	if (numberOfButtons < 2) {
    	return
    }
    
	for (i in 1..numberOfButtons) {
        String childDni = "$device.zigbeeId:${state.buttonEP[i-1]}"
        def componentLabel = getButtonLabel(i)

		log.debug "Creating child $i"
		def child = addChildDevice("Smartthings", "Child Button", childDni, device.hubId,
				[completedSetup: true, label: "${device.displayName} " + componentLabel,
				 isComponent: true, componentName: "Button${i}", componentLabel: componentLabel])

		child.sendEvent(name: "supportedButtonValues", value: state.supportedButtonValues, displayed: false)
		child.sendEvent(name: "numberOfButtons", value: 1, displayed: false)
		child.sendEvent(name: "button", value: "pushed", data: [buttonNumber: 1], displayed: false)
	}
}

def intTo16bitUnsignedHex(value) {
    def hexStr = zigbee.convertToHexString(value.toInteger(),4)
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2))
}

def intTo8bitUnsignedHex(value) {
    return zigbee.convertToHexString(value.toInteger(), 2)
}

private endpointNumber(String deviceId) {
    return deviceId.split(":")[-1] as Integer
}

private boolean isL1aEU1() {
	return device.getDataValue("model") == "lumi.switch.l1aeu1"  
}

private int numberOfSwitches() {
	String model = device.getDataValue("model")
    switch ( model ) 
    {
    	case "lumi.switch.l1aeu1":
    	case "lumi.ctrl_neutral1": //QBKG04LM
    	case "lumi.ctrl_ln1.aq1": //QBKG11LM
		case "lumi.switch.b1lacn02": //QBKG21LM
        case "lumi.switch.b1nacn02": //QBKG23LM
        	state.numberOfSwitches = 1
            break
        case "lumi.ctrl_neutral2": //QBKG03LM
        case "lumi.ctrl_ln2.aq1": //QBKG12LM          
        case "lumi.switch.b2lacn02": //QBKG22LM
        case "lumi.switch.b2nacn02": //QBKG24LM
        	state.numberOfSwitches = 2
            break
        case "lumi.switch.l3acn3": //QBKG25LM
        case "lumi.switch.n3acn3": //QBKG26LM
        	state.numberOfSwitches = 3
            break
        default:
        	state.numberOfSwitches = 1
    }
    log.info ("model $model has $state.numberOfSwitches switches")
    return state.numberOfSwitches 
}

private def displayDebugLog(message) {
	if (true)
    	log.debug "${device.displayName}: ${message}"
}


private initEndpoints() {
	switch ( device.getDataValue("model") ) 
    {
        case "lumi.ctrl_neutral1": //QBKG04LM
		case "lumi.ctrl_neutral2": //QBKG03LM
        case "lumi.switch.b1lacn02": //QBKG21LM
        case "lumi.switch.b2lacn02": //QBKG22LM 
        	state.switchEP = [2, 3]
            state.buttonEP = [4, 5, 6]
            state.supportedButtonValues =  ['pushed', 'held']
            break
            
        case "lumi.switch.n3acn3": //QBKG26LM
        case "lumi.switch.b1nacn02": //QBKG23LM
        case "lumi.switch.b2nacn02": //QBKG24LM
        case "lumi.ctrl_ln1.aq1": //QBKG11LM
        case "lumi.ctrl_ln2.aq1": //QBKG12LM
        case "lumi.switch.l3acn3": //QBKG25LM
        case "lumi.switch.n3acn3": //QBKG26LM
        	state.switchEP = [1, 2, 3]
            state.buttonEP = [5, 6, 7]
            state.supportedButtonValues =  ['pushed', 'double']
            break
            
        case "lumi.switch.l1aeu1": 
        	state.switchEP = [1]
            state.buttonEP = [5]
            state.supportedButtonValues =  ['pushed']
            break
    }
    displayDebugLog("device: $device")
}


private Map dataMap(data)
{
	// convert the catchall data from check-in to a map.
	Map resultMap = [:]
	int maxit = data.size()
    int it = data.indexOf((short)0xff) + 3
    if (true) //( it>3 && data.get(it-4) == 0x01 )
        while ( it < maxit )
        {
            int lbl = 0x00000000 | data.get(it)
            byte type = data.get(it+1)
            switch ( type)
            {
                case DataType.BOOLEAN: 
                    resultMap.put(lbl, (boolean)data.get(it+2))
                    it = it + 3
                    break
                case DataType.INT8:
                case DataType.UINT8:
                	def val = (data.get(it+2))
                    resultMap.put(lbl, (short)val)
                    //resultMap.put(lbl, (short)(0x0000 | data.get(it+2)))
                    it = it + 3
                    break
                case DataType.INT16:
                case DataType.UINT16:
                    resultMap.put(lbl, (int)((data.get(it+3)<<8) | data.get(it+2)))
                    it = it + 4
                    //resultMap.put(lbl, (int)(0x00000000 | (data.get(it+3)<<8) | data.get(it+2)))
                    break
                case DataType.INT32:
                case DataType.UINT32:
                    long x = 0x0000000000000000
                    for ( int i = 0; i < 4; i++ )
                        x |= data.get(it+i+2) << 8*i
                    resultMap.put(lbl, x )
                    it = it + 6
                    break
                case DataType.UINT40:
                    long x = 0x000000000000000
                    for ( int i = 0; i < 5; i++ )
                        x |= data.get(it+i+2) << 8*i
                    resultMap.put(lbl, x )
                    it = it + 7
                    break  
                case DataType.INT64:
                case DataType.UINT64:
                    long x = 0x0000000000000000
                    for ( int i = 0; i < 8; i++ )
                        x |= data.get(it+i+2) << 8*i
                    resultMap.put(lbl, x )
                    it = it + 10
                    break 
                case DataType.FLOAT4:
                    int x = 0x00000000 
                    for ( int i = 0; i < 4; i++ ) 
                        x |= data.get(it+i+2) << 8*i
                    float y = Float.intBitsToFloat(x) 
                    resultMap.put(lbl,y)
                    it = it + 6
                    break
                default: log.warn( "unrecognised type in dataMap: " + zigbee.convertToHexString(type) )
                    return resultMap
            }
        }
    else
    	displayDebugLog("catchall data unrecognised.")
    return resultMap
}

private def parseReportAttributes(map) {
	displayDebugLog( "Map: " + dtMap )

	// see also https://github.com/guyeeba/Hubitat/blob/master/Drivers/Aqara%20QBKG03LM-QBKG04LM.groovy
	switch ( map.clusterId ) 
    {
    	case zigbee.BASIC_CLUSTER: 
         	Map dtMap = dataMap(map.data)
            displayDebugLog( "Map: " + dtMap )
            
            switch (map.data[0]) {
            	case 0x01: 
                    def events = 
                        ( dtMap.get(152) != null ? getWatts( dtMap.get(152) ) : [] ) + 
                        ( dtMap.get(149) != null ? getkWh( dtMap.get(149) ) : [] ) +
                        ( dtMap.get(  1) != null ? getBattery( dtMap.get(1) ): [] ) + 
                        setTemp( dtMap.get(3) )

                    if ( dtMap.get(100) != null )
                    {
                        def childSwitches = getChildSwitches()
                        
                        String switchState = dtMap.get(100) ? "on" : "off" 
                        events << createEvent(name:"switch", value:switchState, displayed: false)
                        
                        def hw = switchState
                        def sw = device.currentValue('switch')
                        
                        for (int i=1; i<state.numberOfSwitches; i++) {
                        	switchState = dtMap.get(100 + i) ? "on" : "off"
                            
                            def childDevice = childSwitches[i-1] //childDevices.find { it.deviceNetworkId.endsWith(":${endpoint}") }.
                			
                            def childStatus = childDevice.currentValue('switch')
                            childDevice.sendEvent(name:"switch", value:switchState,  displayed: false)
                        
                            hw += ", " + switchState
                            sw += ", " + childStatus
                        }
                        
                        displayDebugLog( "Hardware Switch is ${hw}; Software Switch is ${sw}" )
                    }
                    return events
            
            case 0xf0: 
                //state.holdDone = false
                //runIn( 1, doHoldButton )
            	break
            default: 
                displayDebugLog('CatchAll ignored.')
            	break
            }
            break
        case zigbee.ONOFF_CLUSTER:
            //if (!isL1aEU1()) return
            /// for Zigbee 3
            displayDebugLog('L1aEU1 Switch statuses:' + map.data)
            def switchState 
            if (map.command == 0x01) // 0x00, 0x00, 0x00, 0x10(Data.BOOLEAN), 0x??
            	switchState = map.data[-1] == 1 ? "on" : "off"
            else
            	switchState = map.data[0] == 1 ? "on" : "off"    
            return createEvent(name:"switch", value:switchState, descriptionText: descriptionText)
        
        default:
     		log.warn("command ${map.command}\n${map}")
    }
}

private def parseCatchAllMessage(String description) 
{
    Map eventMap = zigbee.getEvent(description)
	Map eventDescMap = zigbee.parseDescriptionAsMap(description)
    log.trace("catchall event ${eventMap}\n${eventDescMap}")
    if (eventMap) {
    	//// this part is from Zigbee multiswitch
		if (eventDescMap && eventDescMap?.attrId == "0000") {//0x0000 : OnOff attributeId
			if (eventDescMap?.sourceEndpoint == "01" || eventDescMap?.endpoint == "01") {
				return eventMap
			} else {
            	log.warning "TBD"
				def childDevice = childDevices.find {
					it.deviceNetworkId == "$device.deviceNetworkId:${eventDescMap.sourceEndpoint}" || it.deviceNetworkId == "$device.deviceNetworkId:${eventDescMap.endpoint}"
				}
				if (childDevice) {
					childDevice.sendEvent(eventMap)
				} else {
					log.warning "Child device: $device.deviceNetworkId:${eventDescMap.sourceEndpoint} was not found"
				}
			}
		}
        return []
        ////   
	}
    
	def map = zigbee.parse(description)
	// https://zigbeealliance.org/wp-content/uploads/2019/12/07-5123-06-zigbee-cluster-library-specification.pdf
    switch (map.command) {
        //case 0x04: Write Attributes Response
        case 0x0a: // Report attributes
        case 0x01: // Read Attributes Response
        case 0x0b: // Default Response
        	return parseReportAttributes(map)
        case 0x07: // Configure Reporting
        	def status = map.data[0]
            if (status == 0x8c)
            	log.error("Configure Reporting: Periodic reports cannot be issued for the attribute ${map.data[2]}. clusterId:${map.clusterId}\n${map}")
            else if (status == 0xc1)
            	log.error("Configure Reporting: An operation was unsuccessful due to a software failure ${map.data[2]}. clusterId:${map.clusterId}\n${map}")
            else if (status != 0x00)
            	log.warn("Configure Reporting clusterId:${map.clusterId}\n${map}")
            break
        case 0x00: // Read Attribute
        	if (map.clusterId == BIND_RESPONSE) break
        default:
     		log.warn("command ${map.command}\n${map}")
    }
    
    if (isBindingTableMessage(description)) {
    		Integer groupAddr = getGroupAddrFromBindingTable(description)
			displayDebugLog("isBindingTableMessage ${groupAddr}")
	
            if (groupAddr != null) {
				List cmds = addHubToGroup(groupAddr)
				return cmds?.collect { new physicalgraph.device.HubAction(it) }
			} else {
				groupAddr = 0x0000
				List cmds = addHubToGroup(groupAddr) +
						zigbee.command(CLUSTER_GROUPS, 0x00, "${zigbee.swapEndianHex(zigbee.convertToHexString(groupAddr, 4))} 00")
				return cmds?.collect { new physicalgraph.device.HubAction(it) }
			}
    }
    
    return []
}

private def parseBindingTableMessage(description) {
	Integer groupAddr = getGroupAddrFromBindingTable(description)
	log.info ("groupAddr ${groupAddr}")
	if (groupAddr) {
		List cmds = addHubToGroup(groupAddr)
		cmds?.collect { new physicalgraph.device.HubAction(it) }
	}
}

private Integer getGroupAddrFromBindingTable(description) {
	log.info "Parsing binding table - '$description'"
	def btr = zigbee.parseBindingTableResponse(description)
	def groupEntry = btr?.table_entries?.find { it.dstAddrMode == 1 }
	if (groupEntry != null) {
		log.info "Found group binding in the binding table: ${groupEntry}"
		Integer.parseInt(groupEntry.dstAddr, 16)
	} else {
		log.info "The binding table does not contain a group binding"
		null
	}
}

private List addHubToGroup(Integer groupAddr) {
	["st cmd 0x0000 0x01 ${CLUSTER_GROUPS} 0x00 {${zigbee.swapEndianHex(zigbee.convertToHexString(groupAddr,4))} 00}",
	 "delay 200"]
}

private List readDeviceBindingTable() {
	["zdo mgmt-bind 0x${device.deviceNetworkId} 0",
	 "delay 200"]
}
