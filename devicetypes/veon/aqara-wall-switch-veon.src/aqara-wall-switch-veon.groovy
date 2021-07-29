// This is the truncated version of 
// https://github.com/aonghus-mor/SmartThingsPublic/blob/master/devicetypes/aonghus-mor/testcode.src/testcode.groovy

import groovy.json.JsonOutput
import physicalgraph.zigbee.zcl.DataType

metadata {
    definition (name: "Aqara Wall Switch (veon)", namespace: "veon", author: "Veon", 
        vid: "1ce7101f-7613-3865-811a-196227c7e4ec", // momentary only with temperature & battery (probably view of tile) 
        ocfDeviceType: "oic.d.switch") {
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
        
        //command "buttonpush"
        //command "doButtonPush"
        
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
        fingerprint profileId: "0104", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b1nacn02", deviceJoinName: "Aqara Switch QBKG23LM"
        // QBKG24LM: two buttons, neutral is required
        fingerprint profileId: "0104", inClusters: "0000,0003,0001,0002,0019,000A", outClusters: "0000,000A,0019", 
                manufacturer: "LUMI", model: "lumi.switch.b2nacn02", deviceJoinName: "Aqara Switch QBKG24LM"
        /*
        zbjoin: {"dni":"CBAB","d":"00158D00051E7A43","capabilities":"8E","endpoints":[{"simple":"01 0104 0051 01 09 0000 0004 0003 0006 0010 0005 000A 0001 0002 02 0019 000A","application":"3D","manufacturer":"LUMI","model":"lumi.switch.b2nacn02"},{"simple":"02 0104 0051 01 02 0006 0010 00","application":"","manufacturer":"","model":""},{"simple":"03 0104 0009 01 01 000C 02 000C 0004","application":"","manufacturer":"","model":""},{"simple":"04 0104 0053 01 01 000C 01 000C","application":"","manufacturer":"","model":""}],"parent":"0000","joinType":1,"joinDurationMs":990,"joinAttempts":1}
        */
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "leftButtonDisconnect", type: "bool", title: "Disconnect left button from switch", defaultValue: false
        input name: "rightButtonDisconnect", type: "bool", title: "Disconnect right button from switch (double button devices)", defaultValue: false
    }
}

def childRefresh(String deviceId)
{
    def endpointId = endpointNumber(deviceId);
    return zigbee.readAttribute (0x0006, 0, [destEndpoint: endpointId])
}

def refresh() {
	log.info "refresh..."
    def cmds = [] // refresh switch state
    getChildDevices()?.each {
        cmds += childRefresh(it.deviceNetworkId)
    }
    
    cmds += zigbee.readAttribute(0x0006, 0, [destEndpoint: state.switchEP[0]] )+ //  indexToSwitchEP(1)
    	  zigbee.readAttribute(0x0002, 0) + 
          //zigbee.readAttribute(0x0000, 0x0007) + // temperature
          zigbee.readAttribute(0x000C, 0x0055) + // power consumption
          zigbee.readAttribute(0x0000, 0xFF22, [mfgCode: "0x115F"]) // disconnected buttons
    
    if (state.numberOfSwitches>1)
          zigbee.readAttribute(0x0000, 0xFF23, [mfgCode: "0x115F"])
          
    sendEvent( name: 'supportedButtonValues', value: ['pushed', 'held', 'up_hold', 'double'], isStateChange: true)
    sendEvent( name: 'checkInterval', value: 3000, data: [ protocol: 'zigbee', hubHardwareId: device.hub.hardwareID ] )

	return cmds
}

def parse(String description) {
	def dat = new Date()
    def newcheck = dat.time
    
    def handled = false
    
    if (description.startsWith("catchall")) {
    	return parseCatchAllMessage(description)
    }
    
    def events = []
	
    if (description.startsWith("on/off")) {
    	return parseCustomMessage(description)
    }
    
    def descMap = zigbee.parseDescriptionAsMap(description)
    def attrId = Integer.parseInt(descMap.attrId, 16)
	def value = Integer.parseInt(descMap.value, 16)
    def cluster = Integer.parseInt(descMap.cluster, 16)
    def endpoint = Integer.parseInt(descMap.endpoint, 16)
    
    switch (cluster) {
    	case 0x000C: //analog input
        	if ( attrId != 0x0055 ) break
            
            float val = Float.intBitsToFloat(Integer.parseInt(descMap.value, 16))
            events = getWatts(val)
        	handled = true
        	break
        case 0x0006:
        case 0x0012:
            events = parseOnOff(descMap)
            handled = true
            break    
        /*case 0x0006: // on/off
    		events = parseOnOff(descMap)
            handled = true
            break*/
        case 0x0002:
        	if (attrId!=0x0)
            events = setTemp( value )
            handled = true
            break
        case 0x0000:
            if (attrId == 0x7) {
            	events = setPowerSource(value)
            } else if (attrId >= 0xff22) {
                events = updateDecoupleStatus(attrId-0xff22, value)
            } else {
            	displayDebugLog("attrId: $attrId")
            }
            handled = events!=[]
            break
        default:
        	log.info("Unknown cluster:$cluster: $description ::: $descMap")
    }
    
    if (!handled) {
    	log.warn("unhandled message: $description")
    }
	
    def now = dat.format("HH:mm:ss EEE dd MMM '('zzz')'", location.timeZone) + "\n" + state.lastPressType
    events << createEvent(name: "lastCheckin", value: now, descriptionText: "Check-In", displayed: debugLogging)
    
    return events
}

def parseOnOff(descMap) {
	def endpoint = Integer.parseInt(descMap.endpoint, 16)
	def attrId = Integer.parseInt(descMap.attrId, 16)
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
        def clickType = valueToClickType(value)

        log.info("Button#$buttonIndex v:$value click:$clickType (Endpoint:$endpoint)")        
        events << createEvent(name: 'button', value: clickType, data:[buttonNumber: buttonIndex ], isStateChange: true)
        
        if (buttonIndex > state.numberOfSwitches) { 
        	log.info("All the buttons are pressed")
            for (int i = 1; i <= state.numberOfSwitches; i++)
        		events << createEvent(name: 'button', value: clickType, data:[buttonNumber: i ], isStateChange: true)
        }
	} else {
		log.warn("parseOnOff ( endpoint: $endpoint, attrId: $attrId, value: $value )")
    }
    return events
}

private def valueToClickType(int value) {
	// QBKG03LM QBKG04LM - {0: pushed}
    // QBKG23LM QBKG24LM - {1: pushed, 2: double}
    def clickTypes = ['pushed', 'pushed', 'double']
    return clickTypes[value]
}

private def parseCustomMessage(String description) 
{
	// QBKG03LM QBKG04LM 
    // posts 'on/off: {0|1}' when any button is held
	if (!description?.startsWith('on/off: ')) {
    	log.warn("Unsupported '$description'")
        return []
    }
    
    def value = (description[-1] == '0') ? 'held' : "up_hold"
    
    log.info("We don't know which button $value, send event to button#1")
    return [createEvent(name: 'button', value: value, data:[buttonNumber: 1], isStateChange: true)]
}

def updateDecoupleStatus(int button, int value) {
    // Disconnect left  button from relay: write uint8 (0x20) value (connected: 0x12, disconnected: 0xFE) to attribute 0xFF22 of endpoint 0x01, cluster 0x0000
	// Disconnect right button from relay: write uint8 (0x20) value (connected: 0x22, disconnected: 0xFE) to attribute 0xFF22 of endpoint 0x01, cluster 0x0000
	log.debug("Update decouple $button = ${Integer.toHexString(value)}")
    
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
        	log.warning "unknown button ${button} ${value}"
            return
    }
   return createEvent(name:"button"+button+"Decoupled", value: decoupled) 
}

private def setTemp(int temp)
{ 
    def event = []
    temp = temp ? temp : 0
    if ( state.tempNow != temp )
    {
      	state.tempNow = temp
        state.tempOffset = tempOffset ? tempOffset : -2
        if ( getTemperatureScale() != "C" ) 
            temp = celsiusToFahrenheit(temp)
        state.tempNow2 = temp + state.tempOffset     
        event << createEvent(name: "temperature", value: state.tempNow2, unit: getTemperatureScale())
        displayDebugLog("Temperature is now ${state.tempNow2}°")          	
	}
    return event
}

private def setPowerSource(int value) {
	def batteryPresent = value != 3
	return [createEvent(name: "powerSource", value: value)]
}

/*
private int toBigEndian(String hex) {
    int ret = 0;
    String hexBigEndian = "";
    if (hex.length() % 2 != 0) return ret;
    for (int i = hex.length() - 2; i >= 0; i -= 2) {
        hexBigEndian += hex.substring(i, i + 2);
    }
    ret = Integer.parseInt(hexBigEndian, 16);
    return ret;
}

private parseXiaomiReport(description) {
}

private parseXiaomiReport_FF01(payload) {
	return;
}

private parseXiaomiReport_0005(payload) {
	displayDebugLog("Xiaomi parse 0005 string = ${payload}")
    
    return new String(payload.decodeHex())
}
*/
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
    return zigbee.command(0x0006, on, "", [destEndpoint: endpointId] )
}

def ping()
{
	displayDebugLog("Pinged")
    zigbee.readAttribute(0x0002, 0)
}

def configure() {
    log.warn "configure..."
    initEndpoints() // reinit endpoints for debug
    
    def cmds = []
    /*
    def children = getChildDevices()
    children?.each{
        def endpointId = endpointNumber(it.deviceNetworkId)
	    cmds += [
            //bindings
            "zdo bind 0x${device.deviceNetworkId} ${endpointId} 1 0x0006 {${device.zigbeeId}} {}", "delay 200",
            //reporting
            "he cr 0x${device.deviceNetworkId} ${endpointId} 0x0006 0 0x10 0 0x3600 {}","delay 200",
    	]
    }
	*/
    cmds += zigbee.writeAttribute(0x0000, 0xFF22, DataType.UINT8, leftButtonDisconnect  ? 0xFE : 0x12, [mfgCode: "0x115F"]) + // disconnected buttons
			zigbee.writeAttribute(0x0000, 0xFF23, DataType.UINT8, rightButtonDisconnect ? 0xFE : 0x22, [mfgCode: "0x115F"]) +
    		refresh()
    
    def numButtons = numberOfSwitches();
    if (numButtons>1) numButtons++ // + click both
    
	sendEvent(name:"numberOfButtons", value: numButtons) 
    
    return cmds
}

def installed() {
	initEndpoints()
    createChildDevices()
	configure()
}

def updated() {
    log.info "updated..."
    sendZigbeeCommands(configure())
}

void sendZigbeeCommands(cmds, delay = 250) {
	cmds.removeAll { it.startsWith("delay") }
	// convert each command into a HubAction
	cmds = cmds.collect { new physicalgraph.device.HubAction(it) }
	sendHubCommand(cmds, delay)
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
        def switchEPId = state.switchEP[i-1] //indexToSwitchEP(i)

        String childDni = "$device.zigbeeId:$switchEPId"
        def componentLabel = device.displayName + "${switchEPId}"

		displayDebugLog(childDni)
        def child = addChildDevice("Smartthings", "Child Switch Health",
        		childDni,
				device.hubId,
				[//completedSetup: true,
				 label         : "$device.displayName Switch $i",
				 isComponent   : false,
				 //componentName : "switch$i",
				 //componentLabel: "Switch $i"
				])
		child.sendEvent(name: "switch", value: "off")
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

private int numberOfSwitches() {
	String model = device.getDataValue("model")
    switch ( model ) 
    {
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
            displayDebugLog("device: $device")
            break
            
        case "lumi.switch.b1nacn02": //QBKG23LM
        case "lumi.switch.b2nacn02": //QBKG24LM
        case "lumi.ctrl_ln1.aq1": //QBKG11LM
        case "lumi.ctrl_ln2.aq1": //QBKG12LM
        case "lumi.switch.l3acn3": //QBKG25LM
        case "lumi.switch.n3acn3": //QBKG26LM
        	state.switchEP = [1, 2, 3]
            state.buttonEP = [5, 6, 7]
            displayDebugLog("device: $device")
            break
    }
}


private Map dataMap(data)
{
	// convert the catchall data from check-in to a map.
	Map resultMap = [:]
	int maxit = data.size()
    int it = data.indexOf((short)0xff) + 3
    if ( it>3 && data.get(it-4) == 0x01 )
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
                case DataType.UINT8:
                    resultMap.put(lbl, (short)(0x0000 | data.get(it+2)))
                    it = it + 3
                    break
                case DataType.UINT16:
                    resultMap.put(lbl, (int)(0x00000000 | (data.get(it+3)<<8) | data.get(it+2)))
                    it = it + 4
                    break
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
                case DataType.UINT64:
                    long x = 0x0000000000000000
                    for ( int i = 0; i < 8; i++ )
                        x |= data.get(it+i+2) << 8*i
                    resultMap.put(lbl, x )
                    it = it + 10
                    break 
                case DataType.INT8:
                    resultMap.put(lbl, (short)(data.get(it+2)))
                    it = it + 3
                    break
                 case DataType.INT16:
                    resultMap.put(lbl, (int)((data.get(it+3)<<8) | data.get(it+2)))
                    it = it + 4
                    break
                case DataType.FLOAT4:
                    int x = 0x00000000 
                    for ( int i = 0; i < 4; i++ ) 
                        x |= data.get(it+i+2) << 8*i
                    float y = Float.intBitsToFloat(x) 
                    resultMap.put(lbl,y)
                    it = it + 6
                    break
                default: displayDebugLog( "unrecognised type in dataMap: " + zigbee.convertToHexString(type) )
                    return resultMap
            }
        }
    else
    	displayDebugLog("catchall data unrecognised.")
    return resultMap
}

private def parseCatchAllMessage(String description) 
{
	def cluster = zigbee.parse(description)
	def events = []
    
    displayDebugLog("Xiaomi parse string = ${cluster}")
	// see also https://github.com/guyeeba/Hubitat/blob/master/Drivers/Aqara%20QBKG03LM-QBKG04LM.groovy
    switch ( cluster.clusterId ) 
    {
    	case 0x0000: 
         	if ( cluster.command != 0x0a ) break
            Map dtMap = dataMap(cluster.data)
            displayDebugLog( "Map: " + dtMap )
            
            if (cluster.data[0] == 0x01 )
            {
                events = events + setTemp( dtMap.get(3) ) +
                    ( dtMap.get(152) != null ? getWatts( dtMap.get(152) ) : [] ) + 
                    ( dtMap.get(149) != null ? getkWh( dtMap.get(149) ) : [] ) +
                    ( dtMap.get(100) != null ? [] : getBattery( dtMap.get(1) ) )

                if ( dtMap.get(100) != null )
                {
                    def onoff = (dtMap.get(100) ? "on" : "off")
                    switch ( state.numberOfSwitches )
                    {
                        case 1:
                            displayDebugLog( "Hardware Switch is ${onoff}; Software Switch is " + device.currentValue('switch') )
                        break
                        
                        case 2:
                            def onoff2 = (dtMap.get(101) ? 'on' : 'off' )
                            def child = getChildDevices()[0]
                            displayDebugLog( "Hardware Switches are (" + onoff + "," + onoff2 +"); Software Switches are (" + device.currentValue('switch') + ',' + child.device.currentValue('switch') + ')' )
                        break
                        
                        case 3:
                            def onoff2 = (dtMap.get(101) ? 'on' : 'off' )
                            def child2 = getChild(0)
                            def onoff3 = (dtMap.get(102) ? 'on' : 'off' )
                            def child3 = getChild(1)
                            //displayDebugLog( "Decoupled Switches: ${state.decoupled}" )
                            displayDebugLog( "Hardware Switches are (${onoff}, ${onoff2}, ${onoff3}); Software Switches are (" + device.currentValue('switch') + ',' + child2.device.currentValue('switch') + ',' + child3.device.currentValue('switch')+ ')' )
                        break

                        default:
                            displayDebugLog("Number of switches unrecognised: ${state.numberOfSwitches}")
                    }
                }
            }
            else if ( cluster.data[0] == 0xf0 )
            {
                state.holdDone = false
                runIn( 1, doHoldButton )
            }
            else
            {
                displayDebugLog('CatchAll ignored.')
            }
            
        	break
        case 0x0006:
            displayDebugLog('Switch statuses:' + cluster.data)
    }
    return events
}