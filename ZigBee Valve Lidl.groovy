/**
 *  Copyright 2016 SmartThings
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
import physicalgraph.zigbee.clusters.iaszone.ZoneStatus
import physicalgraph.zigbee.zcl.DataType

metadata {
    definition (name: "ZigBee Valve Lidl", namespace: "smartthings", author: "SmartThings-MCC", runLocally: true, minHubCoreVersion: '000.017.0012', executeCommandsLocally: false) {
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "Power Source"
        capability "Health Check"
        capability "Refresh"
        capability "Valve"

        fingerprint profileId: "0104", deviceId: "0000", inClusters: "0000, 0004, 0005, 0006, 0003, EF00", outClusters: "0019, 000A", manufacturer: "TZE200_htnnfasr", model: "TS0601", deviceJoinName: "Valve" //Water Valve Actuator Lidl
        fingerprint profileId: "0104", inClusters: "0000, 0004, 0005, 0006", outClusters: "0019", manufacturer: "", model: "TS0011", deviceJoinName: "Valve" //Smart Gas & Water Valve Actuator
        fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0006, 0020, 0B02, FC02", outClusters: "0019", manufacturer: "WAXMAN", model: "leakSMART Water Valve v2.10", deviceJoinName: "leakSMART Valve" //leakSMART Valve
        fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0004, 0005, 0006, 0008, 000F, 0020, 0B02", outClusters: "0003, 0019", manufacturer: "WAXMAN", model: "House Water Valve - MDL-TBD", deviceJoinName: "Waxman Valve" //Waxman House Water Valve
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0006, 0500", outClusters: "0019", manufacturer: "", model: "E253-KR0B0ZX-HA", deviceJoinName: "Valve" //Smart Gas Valve Actuator
    }

    // simulator metadata
    simulator {
        // status messages
        status "on": "on/off: 1"
        status "off": "on/off: 0"

        // reply messages
        reply "zcl on-off on": "on/off: 1"
        reply "zcl on-off off": "on/off: 0"
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"contact", type: "generic", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.contact", key: "PRIMARY_CONTROL") {
                attributeState "open", label: '${name}', action: "valve.close", icon: "st.valves.water.open", backgroundColor: "#00A0DC", nextState:"closing"
                attributeState "closed", label: '${name}', action: "valve.open", icon: "st.valves.water.closed", backgroundColor: "#ffffff", nextState:"opening"
                attributeState "opening", label: '${name}', action: "valve.close", icon: "st.valves.water.open", backgroundColor: "#00A0DC", nextState:"closing"
                attributeState "closing", label: '${name}', action: "valve.open", icon: "st.valves.water.closed", backgroundColor: "#ffffff", nextState:"opening"
            }
            tileAttribute ("powerSource", key: "SECONDARY_CONTROL") {
                attributeState "powerSource", label:'Power Source: ${currentValue}'
            }
        }

        valueTile("battery", "device.battery", inactiveLabel:false, decoration:"flat", width:2, height:2) {
            state "battery", label:'${currentValue}% battery', unit:""
        }

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["contact"])
        details(["contact", "battery", "refresh"])
    }
}

private getCLUSTER_BASIC() { 0x0000 }
private getBASIC_ATTR_POWER_SOURCE() { 0x0007 }
private getCLUSTER_POWER() { 0x0001 }
//private getPOWER_ATTR_BATTERY_PERCENTAGE_REMAINING() { 0x0021 }
private getBATTERY_VOLTAGE_VALUE_ATTRIBUTE() { 0x0020 }

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug "description is $description"
    def event = zigbee.getEvent(description)
    if (event) {
        if(event.name == "switch") {
            event.name = "contact"                  //0006 cluster in valve is tied to contact
            if(event.value == "on") {
                event.value = "open"
            }
            else if(event.value == "off") {
                event.value = "closed"
            }
            sendEvent(event)
            // we need a valve and a contact event every time
            event.name = "valve"
        } else if (event.name == "powerSource") {
            event.value = event.value.toLowerCase()
        }
        sendEvent(event)
    }
    else {
        def descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap.clusterInt == CLUSTER_BASIC && descMap.attrInt == BASIC_ATTR_POWER_SOURCE){
            def value = descMap.value
            log.debug "Source value= $value"
            if (value == "01" || value == "02") {
                sendEvent(name: "powerSource", value: "mains")
            }
            else if (value == "03") {
                sendEvent(name: "powerSource", value: "battery")
            }
            else if (value == "04") {
                sendEvent(name: "powerSource", value: "dc")
            }
            else {
                sendEvent(name: "powerSource", value: "unknown")
            }
        }
        else if (descMap.clusterInt == CLUSTER_POWER && descMap.attrInt == BATTERY_VOLTAGE_VALUE_ATTRIBUTE && descMap.commandInt != 0x07) {
        //else if (descMap.clusterInt == CLUSTER_POWER && descMap.attrInt == POWER_ATTR_BATTERY_PERCENTAGE_REMAINING) {
	    log.debug 'Battery'
	    def linkText = getLinkText(device)

	    def rawValue = Integer.parseInt(descMap.value, 16)
        def volts = rawValue / 10
        log.debug "rawValue= $rawValue" 
	    if (!(rawValue == 0 || rawValue == 255)) {
		 def minVolts = 2.3
         def maxVolts = 3.0
		 def pct = (volts - minVolts) / (maxVolts - minVolts)
		 def roundedPct = Math.round(pct * 100)
		 if (roundedPct <= 0)
			roundedPct = 1
        event.value = Math.min(100, roundedPct) 
		log.debug "${linkText} battery was ${event.value}%"
        event.name = "battery"
        //event.value = Math.round(Integer.parseInt(descMap.value, 16) / 2)
         sendEvent(event)
        }
        else {
            log.warn "DID NOT PARSE MESSAGE for description : $description"
            log.debug descMap
        }
    }
}
}
def open() {
    zigbee.on()
}

def close() {
    zigbee.off()
}

def refresh() {
    log.debug "refresh called"

    def cmds = []
    cmds += zigbee.onOffRefresh()
    cmds += zigbee.readAttribute(CLUSTER_BASIC, BASIC_ATTR_POWER_SOURCE)
    //cmds += zigbee.readAttribute(CLUSTER_POWER, POWER_ATTR_BATTERY_PERCENTAGE_REMAINING)
    cmds += zigbee.readAttribute(CLUSTER_POWER, BATTERY_VOLTAGE_VALUE_ATTRIBUTE)
    cmds += zigbee.onOffConfig()
    cmds += zigbee.configureReporting(CLUSTER_BASIC, BASIC_ATTR_POWER_SOURCE, DataType.ENUM8, 5, 600, 1)
    //cmds += zigbee.configureReporting(CLUSTER_POWER, POWER_ATTR_BATTERY_PERCENTAGE_REMAINING, DataType.UINT8, 600, 21600, 1)
    cmds += zigbee.configureReporting(CLUSTER_POWER, BATTERY_VOLTAGE_VALUE_ATTRIBUTE, DataType.UINT8, 600, 21600, 1)
    return cmds
}

def configure() {
    log.debug "Configuring Reporting and Bindings."
    refresh()
}

def installed() {
    sendEvent(name: "checkInterval", value: 1 * 10 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
}

def ping() {
    zigbee.onOffRefresh()
}
