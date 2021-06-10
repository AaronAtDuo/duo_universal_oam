package com.duosecurity.plugin;

import java.util.Map;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;
import java.lang.reflect.*;
import org.junit.*;
import static org.junit.Assert.*;

public class DuoPluginLogSanitizationTest {
    @Test
    public void testSanitizeEmailInputUnchanged() {
        String testString = "a_good_user@example.com";
        String expectedResult = testString;

        String actualResult = DuoPlugin.sanitizeForLogging(testString);

        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void testSanitizeAlphanumOnlyUnchanged() {
        String testString = "agooduser001";
        String expectedResult = testString;

        String actualResult = DuoPlugin.sanitizeForLogging(testString);

        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void testSanitizeAlphanumMixedCaseUnchanged() {
        String testString = "JamesBond007";
        String expectedResult = testString;

        String actualResult = DuoPlugin.sanitizeForLogging(testString);

        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void testSanitizeNewlinesRemoved() {
        String testString = "One\nTwo\nThree";
        String expectedResult = "OneTwoThree";

        String actualResult = DuoPlugin.sanitizeForLogging(testString);

        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void testSanitizeSpecialCharactersRemoved() {
        String testString = "One:Two\\Three:Four#Five*Six@Seven;";
        String expectedResult = "OneTwoThreeFourFiveSix@Seven";

        String actualResult = DuoPlugin.sanitizeForLogging(testString);

        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void testSanitizeNull() {
        String testString = null;
        String expectedResult = "";

        String actualResult = DuoPlugin.sanitizeForLogging(testString);

        assertEquals(expectedResult, actualResult);
    }
}