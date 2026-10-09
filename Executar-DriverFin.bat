@echo off
title DriverFin Java Desktop
cd /d "%~dp0dist\DriverFinJava"
"%~dp0dist\DriverFinJava\runtime\bin\java.exe" -cp "%~dp0dist\DriverFinJava\app\driverfin-java-1.0-SNAPSHOT.jar" com.driverfin.Main
