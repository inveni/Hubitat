metadata {
    definition (name: "AXIS Gear V3 Hubitat", namespace: "axis", author: "AXIS Labs", importUrl: "https://raw.githubusercontent.com/bptworld/Hubitat/master/Ported/Axis%20Gear/Axis%20Gear%20V3%20Driver.groovy") {  
        capability "Actuator"
        capability "Configuration"
        capability "Switch"
        capability "Switch Level"
        capability "Refresh"        
        capability "Battery"
        capability "HealthCheck"
        capability "Window Shade"
		
        //Custom Commandes to achieve 25% increment control
        command "ShadesUp"
        command "ShadesDown"
           
        fingerprint profileId: "0200", inClusters: "0000, 0001, 0004, 0005, 0006, 0008, 0100, 0102", manufacturer: "AXIS", model: "GR-ZB01-W", deviceJoinName: "AXIS Gear"
        //ClusterIDs: 0000 - Basic; 0004 - Groups; 0005 - Scenes; 0006 - On/Off; 0008 - Level Control; 0100 - Shade Configuration; 0102 - Window Covering;
        //Updated 2017-06-21
        //Updated 2017-08-24 - added power cluster 0001 - added battery, level, reporting, & health check
        //Updated 2018-01-04 - Axis Inversion & Increased Battery Reporting interval to 1 hour (previously 5 mins)
        //Updated 2018-01-08 - Updated battery conversion from [0-100 : 00 - 64] to [0-100 : 00-C8] to reflect firmware update
        //Updated 2018-11-01 - added in configure reporting for refresh button, close when press on partial shade icon, update handler to parse between 0-254 as a percentage
	}	
	preferences() {    	
        section(""){
            input "logEnable", "bool", title: "Enable logging", required: true, defaultValue: true
        }
    }
}
 
//Declare Clusters
private getCLUSTER_POWER() {0x0001}
private getCLUSTER_LEVEL() {0x0008}
private getLEVEL_ATTR_LEVEL() {0x0000}
private getPOWER_ATTR_BATTERY() {0x0021}

//Custom command to increment blind position by 25 %
def ShadesUp(){
	def shadeValue = device.latestValue("level") as Integer ?: 0 
    
    if (shadeValue < 100){
      	shadeValue = Math.min(25 * (Math.round(shadeValue / 25) + 1), 100) as Integer
    }else { 
    	shadeValue = 100
	}
    //sendEvent(name:"level", value:shadeValue, displayed:true)
    setLevel(shadeValue)
	
	setLevel(shadeValue)
}

//Custom command to decrement blind position by 25 %
def ShadesDown(){
	def shadeValue = device.latestValue("level") as Integer ?: 0 
    
    if (shadeValue > 0){
      	shadeValue = Math.max(25 * (Math.round(shadeValue / 25) - 1), 0) as Integer
    }else { 
    	shadeValue = 0
	}
    //sendEvent(name:"level", value:shadeValue, displayed:true)
    setLevel(shadeValue)
	
	setLevel(shadeValue)   
}

//Send Command through setLevel()
def on() {
	//sendEvent(name:"level", value:0, displayed:true)
    setLevel(100)
	
	setLevel(100)
}

//Send Command through setLevel()
def off() {
	setLevel(0)
	
	setLevel(0)
}
//Command to set the blind position (%) and log the event
def setLevel(value) {
	sendEvent(name:"level", value: value, displayed:true)
    def L = Math.round(value);
    def i = Integer.valueOf(L.intValue());
    setWindowShade(i)
	zigbee.setLevel(i)
    
	zigbee.setLevel(i)
}
//Send Command through setLevel()
def open() {
    setLevel(100)
	
	setLevel(100)
}
//Send Command through setLevel()
def close() {
	setLevel(0)
	
	setLevel(0)
}

//Reporting of Battery & position levels
def ping(){
	if(logEnable) log.debug "Ping() "
    return zigbee.readAttribute(CLUSTER_POWER, POWER_ATTR_BATTERY) +
    	   zigbee.readAttribute(CLUSTER_LEVEL, LEVEL_ATTR_LEVEL)
    
}

//Set blind State based on position (which shows appropriate image) 
def setWindowShade(value){
 if ((value>0)&&(value<99)){
    	sendEvent(name:"windowShade", value: "partial", displayed:true)
    } else if (value >= 99){
    	sendEvent(name:"windowShade", value: "open", displayed:true)
    }else{
    	sendEvent(name:"windowShade", value: "closed", displayed:true)
    }
}

//Refresh command
def refresh() {
	if(logEnable) log.debug "parse() refresh"
    return zigbee.configureReporting(CLUSTER_POWER, POWER_ATTR_BATTERY, 0x20, 1, 3600, 0x01) +
           zigbee.configureReporting(CLUSTER_LEVEL, LEVEL_ATTR_LEVEL, 0x20, 1, 3600, 0x01) + 
           zigbee.readAttribute(CLUSTER_POWER, POWER_ATTR_BATTERY) +
    	   zigbee.readAttribute(CLUSTER_LEVEL, LEVEL_ATTR_LEVEL)
}
//configure reporting
def configure() {
    if(logEnable) log.debug "Configuring Reporting and Bindings."
    sendEvent(name: "checkInterval", value: 60, displayed: true, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    def cmds = 
    	zigbee.configureReporting(CLUSTER_POWER, POWER_ATTR_BATTERY, 0x20, 1, 3600, 0x01) +
        zigbee.configureReporting(CLUSTER_LEVEL, LEVEL_ATTR_LEVEL, 0x20, 1, 3600, 0x01)
        log.info "configure() --- cmds: $cmds"
    return refresh + cmds
}

def parse(String description) {
    log.trace "parse() --- description: $description"


    Map map = [:]
    if (description?.startsWith('read attr -')) {
        map = parseReportAttributeMessage(description)
    } else if (description?.startsWith('attr report -')) {
        map = parseReportAttributeMessage(description)
    }

    def result = map ? createEvent(map) : null
    if(logEnable) log.debug "parse() --- returned: $result"
    return result
}

private Map parseReportAttributeMessage(String description) {
    Map descMap = zigbee.parseDescriptionAsMap(description)
    Map resultMap = [:]
    if (descMap.clusterInt == CLUSTER_POWER && descMap.attrInt == POWER_ATTR_BATTERY) {
        resultMap.name = "battery"
        def batteryValue = Math.round((Integer.parseInt(descMap.value, 16))/2)
        if(logEnable) log.debug "parseDescriptionAsMap() --- Battery: $batteryValue"
        if ((batteryValue >= 0)&&(batteryValue <= 100)){
        	resultMap.value = batteryValue
        }
        
    }
    else if (descMap.clusterInt == CLUSTER_LEVEL && descMap.attrInt == LEVEL_ATTR_LEVEL) {
        resultMap.name = "level"
        def levelValue = Math.round(Integer.parseInt(descMap.value, 16))
        def levelValuePercent = Math.round((levelValue/255)*100)
        //Set icon based on device feedback for the  open, closed, & partial configuration
        setWindowShade(levelValuePercent)
        resultMap.value = levelValuePercent
    }
    else {
        if(logEnable) log.debug "parseReportAttributeMessage() --- ignoring attribute"
    }
    return resultMap
}
