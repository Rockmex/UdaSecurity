package com.udacity.security.service;

import com.udacity.image.service.ImageService;
import com.udacity.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

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
    @ParameterizedTest
    @ValueSource(booleans = {true,false})
    public void checkAlarmStatus_AlarmActiveChangeSensorState_NoAlarmStatusChanged(boolean active){
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor,active);
        assertEquals(AlarmStatus.ALARM,securityRepository.getAlarmStatus());
    }

    //Test5: If a sensor is activated while already active and the system is in pending state, change it to alarm state.
    @Test   //???   (May need to fix method)
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
    @ParameterizedTest   //??? (May need to fix method)
    @ValueSource(ints = {1,3,5})
    public void changeAlarmStatus_ImageNotContainingCatAndSensorNotActive_SetStatusToNoAlarm(int sensorNum) {
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < sensorNum; i++){
            sensors.add(new Sensor("sensor"+i,SensorType.DOOR));
        }
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(false);
        when(securityRepository.getSensors()).thenReturn(sensors);
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
    @ParameterizedTest      //??? (Maybe need adding new method to SecurityService)
    @ValueSource(ints = {1,3,5})
    public void resetSensorsState_ArmingStatusArmed_SetAllSensorsToInactive(int sensorNum){
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < sensorNum; i++){
            Sensor dummySensor = new Sensor("sensor"+i,SensorType.DOOR);
            if (i%2==0){
                dummySensor.setActive(true);
            }
            sensors.add(dummySensor);
        }
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.NO_ALARM));    //This is dummy code to make test fail. Fix later
    }

    //Test11: If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
    @Test
    public void changeAlarmStatus_ArmingStatusArmedHomeAndCatDetected_SetStatusToAlarm(){
        when(securityService.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(true);
        securityService.processImage(bufferedImage);
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.ALARM));
    }

}