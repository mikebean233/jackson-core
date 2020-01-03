package com.fasterxml.jackson.core.read;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.json.JsonReadFeature;

public class NonStandardParserFeaturesTest
    extends com.fasterxml.jackson.core.BaseTest
{
    @SuppressWarnings("deprecation")
    public void testDefaults() {
        JsonFactory f = new JsonFactory();
        assertFalse(f.isEnabled(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS));
        assertFalse(f.isEnabled(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS));
    }

    public void testNonStandardNameChars() throws Exception
    {
        _testNonStandardNameChars(MODE_INPUT_STREAM);
        _testNonStandardNameChars(MODE_INPUT_STREAM_THROTTLED);
        _testNonStandardNameChars(MODE_DATA_INPUT);
        _testNonStandardNameChars(MODE_READER);
    }

    public void testNonStandardAnyCharQuoting() throws Exception
    {
        _testNonStandarBackslashQuoting(MODE_INPUT_STREAM);
        _testNonStandarBackslashQuoting(MODE_INPUT_STREAM_THROTTLED);
        _testNonStandarBackslashQuoting(MODE_DATA_INPUT);
        _testNonStandarBackslashQuoting(MODE_READER);
    }

    public void testLeadingZeroesUTF8() throws Exception {
        _testLeadingZeroes(MODE_INPUT_STREAM, false);
        _testLeadingZeroes(MODE_INPUT_STREAM, true);
        _testLeadingZeroes(MODE_INPUT_STREAM_THROTTLED, false);
        _testLeadingZeroes(MODE_INPUT_STREAM_THROTTLED, true);

        // 17-May-2016, tatu: With DataInput, must have trailing space
        //   since there's no way to detect end of input
        _testLeadingZeroes(MODE_DATA_INPUT, true);
    }

    public void testLeadingZeroesReader() throws Exception {
        _testLeadingZeroes(MODE_READER, false);
        _testLeadingZeroes(MODE_READER, true);
    }

    // allow NaN
    public void testAllowNaN() throws Exception {
        _testAllowNaN(MODE_INPUT_STREAM);
        _testAllowNaN(MODE_INPUT_STREAM_THROTTLED);
        _testAllowNaN(MODE_DATA_INPUT);
        _testAllowNaN(MODE_READER);
    }

    // allow +Inf/-Inf
    public void testAllowInfinity() throws Exception {
        _testAllowInf(MODE_INPUT_STREAM);
        _testAllowInf(MODE_INPUT_STREAM_THROTTLED);
        _testAllowInf(MODE_DATA_INPUT);
        _testAllowInf(MODE_READER);
    }

    /*
    /****************************************************************
    /* Secondary test methods
    /****************************************************************
     */
    
    private void _testNonStandardNameChars(int mode) throws Exception
    {
        final JsonFactory f = JsonFactory.builder()
                .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                .build();
        String JSON = "{ @type : \"mytype\", #color : 123, *error* : true, "
            +" hyphen-ated : \"yes\", me+my : null"
            +"}";
        JsonParser p = createParser(f, mode, JSON);

        assertToken(JsonToken.START_OBJECT, p.nextToken());

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("@type", p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("mytype", p.getText());

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("#color", p.getText());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(123, p.getIntValue());

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("*error*", p.getText());
        assertToken(JsonToken.VALUE_TRUE, p.nextToken());

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("hyphen-ated", p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("yes", p.getText());

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("me+my", p.getText());
        assertToken(JsonToken.VALUE_NULL, p.nextToken());
    
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }

    private void _testNonStandarBackslashQuoting(int mode) throws Exception
    {
        // first: verify that we get an exception
        JsonFactory f = new JsonFactory();
        final String JSON = quote("\\'");
        JsonParser p = createParser(f, mode, JSON);
        try {      
            p.nextToken();
            p.getText();
            fail("Should have thrown an exception for doc <"+JSON+">");
        } catch (JsonParseException e) {
            verifyException(e, "unrecognized character escape");
        } finally {
            p.close();
        }
        // and then verify it's ok...
        f = f.rebuild()
                .configure(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
                .build();
        p = createParser(f, mode, JSON);
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("'", p.getText());
        p.close();
    }

    private void _testLeadingZeroes(int mode, boolean appendSpace) throws Exception
    {
        // first: verify that we get an exception
        JsonFactory f = new JsonFactory();
        String JSON = "00003";
        if (appendSpace) {
            JSON += " ";
        }
        JsonParser p = createParser(f, mode, JSON);
        try {      
            p.nextToken();
            p.getText();
            fail("Should have thrown an exception for doc <"+JSON+">");
        } catch (JsonParseException e) {
            verifyException(e, "invalid numeric value");
        } finally {
            p.close();
        }

        // and then verify it's ok when enabled
        f = JsonFactory.builder()
                .configure(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS, true)
                .build();
        p = createParser(f, mode, JSON);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(3, p.getIntValue());
        assertEquals("3", p.getText());
        p.close();
    
        // Plus, also: verify that leading zero magnitude is ok:
        JSON = "0"+Integer.MAX_VALUE;
        if (appendSpace) {
            JSON += " ";
        }
        p = createParser(f, mode, JSON);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(String.valueOf(Integer.MAX_VALUE), p.getText());
        assertEquals(Integer.MAX_VALUE, p.getIntValue());
        Number nr = p.getNumberValue();
        assertSame(Integer.class, nr.getClass());
        p.close();
    }

    private void _testAllowNaN(int mode) throws Exception
    {
        final String JSON = "[ NaN]";
        JsonFactory f = new JsonFactory();

        // without enabling, should get an exception
        JsonParser p = createParser(f, mode, JSON);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        try {
            p.nextToken();
            fail("Expected exception");
        } catch (Exception e) {
            verifyException(e, "non-standard");
        } finally {
            p.close();
        }

        // we can enable it dynamically (impl detail)
        f = JsonFactory.builder()
                .configure(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS, true)
                .build();
        p = createParser(f, mode, JSON);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        
        double d = p.getDoubleValue();
        assertTrue(Double.isNaN(d));
        assertEquals("NaN", p.getText());

        // [Issue#98]
        try {
            /*BigDecimal dec =*/ p.getDecimalValue();
            fail("Should fail when trying to access NaN as BigDecimal");
        } catch (NumberFormatException e) {
            verifyException(e, "can not be represented as BigDecimal");
        }
       
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();

        // finally, should also work with skipping
        f = JsonFactory.builder()
                .configure(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS, true)
                .build();
        p = createParser(f, mode, JSON);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }

    private void _testAllowInf(int mode) throws Exception
    {
        final String JSON = "[ -INF, +INF, +Infinity, Infinity, -Infinity ]";
        JsonFactory f = new JsonFactory();

        // without enabling, should get an exception
        JsonParser p = createParser(f, mode, JSON);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        try {
            p.nextToken();
            fail("Expected exception");
        } catch (Exception e) {
            verifyException(e, "Non-standard token '-INF'");
        } finally {
            p.close();
        }
        f = JsonFactory.builder()
                .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
                .build();
        p = createParser(f, mode, JSON);
        assertToken(JsonToken.START_ARRAY, p.nextToken());

        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        double d = p.getDoubleValue();
        assertEquals("-INF", p.getText());
        assertTrue(Double.isInfinite(d));
        assertTrue(d == Double.NEGATIVE_INFINITY);

        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        d = p.getDoubleValue();
        assertEquals("+INF", p.getText());
        assertTrue(Double.isInfinite(d));
        assertTrue(d == Double.POSITIVE_INFINITY);

        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        d = p.getDoubleValue();
        assertEquals("+Infinity", p.getText());
        assertTrue(Double.isInfinite(d));
        assertTrue(d == Double.POSITIVE_INFINITY);

        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        d = p.getDoubleValue();
        assertEquals("Infinity", p.getText());
        assertTrue(Double.isInfinite(d));
        assertTrue(d == Double.POSITIVE_INFINITY);

        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        d = p.getDoubleValue();
        assertEquals("-Infinity", p.getText());
        assertTrue(Double.isInfinite(d));
        assertTrue(d == Double.NEGATIVE_INFINITY);

        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();

        // finally, should also work with skipping
        f = JsonFactory.builder()
                .configure(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS, true)
                .build();
        p = createParser(f, mode, JSON);

        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        
        p.close();
    }
}
