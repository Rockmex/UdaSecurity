package com.udacity.security.service;

import com.udacity.image.service.ImageService;
import com.udacity.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    @Mock
    SecurityRepository securityRepository;

    @Mock
    ImageService imageService;

    @Mock
    BufferedImage bufferedImage;

    private Sensor sensor;

    private SecurityService securityService;

    @BeforeEach
    void init(){
        this.securityService = new SecurityService(securityRepository, imageService);
        this.sensor = new Sensor("sensor", SensorType.DOOR);
    }

    //Test1: If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
    @ParameterizedTest
    @EnumSource(value=ArmingStatus.class,names={"ARMED_HOME","ARMED_AWAY"})
    public void changeAlarmStatus_ArmingArmedAndSensorActivated_SetStatusToPending(ArmingStatus armingStatus){
        when(securityService.getArmingStatus()).thenReturn(armingStatus);
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor,true);
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.PENDING_ALARM));
    }

    //Test2: If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
    @ParameterizedTest
    @EnumSource(value=ArmingStatus.class,names={"ARMED_HOME","ARMED_AWAY"})
    public void changeAlarmStatus_ArmingArmedAndSensorActivatedAndAlarmPending_SetStatusToAlarm(ArmingStatus armingStatus){
        when(securityService.getArmingStatus()).thenReturn(armingStatus);
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor,true);
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.ALARM));
    }

    //Test3: If pending alarm and all sensors are inactive, return to no alarm state.
    @Test
    public void changeAlarmStatus_AlarmPendingAndSensorInactive_SetStatusToNoAlarm(){
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true); //Set sensor to true because it was initially active, changes are made on the next line!!
        securityService.changeSensorActivationStatus(sensor,false);
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.NO_ALARM));
    }

    //Test4: If alarm is active, change in sensor state should not affect the alarm state.
    //Concrete Example: Arm the system and activate two sensors, the system should go to alarm state. Then deactivate one sensor and the system should not change alarm state.
    @ParameterizedTest
    @ValueSource(booleans = {true,false})
    public void checkAlarmStatus_AlarmActiveChangeSensorState_NoAlarmStatusChanged(boolean active){
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        Sensor sensor1 = new Sensor("sensor1", SensorType.DOOR);
        Sensor sensor2 = new Sensor("sensor2",SensorType.DOOR);
        sensor1.setActive(true);
        sensor2.setActive(true);
        securityService.changeSensorActivationStatus(sensor1,active);
        verify(securityRepository,never()).setAlarmStatus(any(AlarmStatus.class));
    }

    //Test5: If a sensor is activated while already active and the system is in pending state, change it to alarm state.
    @Test
    public void changeAlarmStatus_SensorActivatedWhileActiveAndAlarmStatusPending_SetStatusToAlarm(){
        sensor.setActive(true);
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor,true);
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.ALARM));
    }

    //Test6: If a sensor is deactivated while already inactive, make no changes to the alarm state.
    @Test
    public void checkAlarmStatus_SensorDeactivatedWhileInactive_NoAlarmStatusChanged(){
        securityService.changeSensorActivationStatus(sensor,false);
        verify(securityRepository,never()).setAlarmStatus(any(AlarmStatus.class));
    }

    //Test7: If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
    @Test
    public void changeAlarmStatus_ImageContainingCatAndArmingStatusArmedHome_SetStatusToAlarm(){
        when(securityService.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(true);
        securityService.processImage(bufferedImage);
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.ALARM));
    }

    //Test8: If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.
    @ParameterizedTest
    @ValueSource(ints = {1,3,5})
    public void changeAlarmStatus_ImageNotContainingCatAndSensorNotActive_SetStatusToNoAlarm(int sensorNum) {
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < sensorNum; i++){
            sensors.add(new Sensor("sensor"+i,SensorType.DOOR));
        }
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(false);
        when(securityRepository.getSensors()).thenReturn(sensors);
        for (Sensor testSensor : securityService.getSensors()){
            assertEquals(false,testSensor.getActive());
        }
        securityService.processImage(bufferedImage);
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.NO_ALARM));
    }

    //Test9: If the system is disarmed, set the status to no alarm.
    @Test
    public void changeAlarmStatus_ArmingStatusDisarmed_SetStatusToNoAlarm(){
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.NO_ALARM));
    }

    //Test10: If the system is armed, reset all sensors to inactive.
    //Concrete Example: put all sensors to the active state when disarmed, and then put the system in the armed state, sensors should be inactivated
    @ParameterizedTest
    @MethodSource("nTestsForArmingStatus")
    public void resetSensorsState_ArmingStatusArmed_SetAllSensorsToInactive(int sensorNum, ArmingStatus armingStatus){
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < sensorNum; i++){
            Sensor dummySensor = new Sensor("sensor"+i,SensorType.DOOR);
            dummySensor.setActive(true);
            sensors.add(dummySensor);
        }
        when(securityService.getSensors()).thenReturn(sensors);
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.setArmingStatus(armingStatus);
        for (Sensor testSensor : securityService.getSensors()){
            assertEquals(false,testSensor.getActive());
        }
        verify(securityRepository,times(sensorNum)).updateSensor(any(Sensor.class));
    }

    //Method Source for Test10.
    private static Stream<Arguments> nTestsForArmingStatus() {
        return Stream.of(
                arguments(1, ArmingStatus.ARMED_HOME),
                arguments(2, ArmingStatus.ARMED_HOME),
                arguments(12, ArmingStatus.ARMED_HOME),
                arguments(1, ArmingStatus.ARMED_AWAY),
                arguments(2, ArmingStatus.ARMED_AWAY),
                arguments(12, ArmingStatus.ARMED_AWAY)
        );
    }

    //Test11: If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
    //Concrete Example: Put the system as disarmed, scan a picture until it detects a cat, after that make it armed, it should make system in ALARM state
    @Test
    public void changeAlarmStatus_ArmingStatusArmedHomeAndCatDetected_SetStatusToAlarm(){
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(true);
        securityService.processImage(bufferedImage);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.ALARM));
    }

}