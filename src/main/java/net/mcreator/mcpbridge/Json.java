package net.mcreator.mcpbridge;

import java.util.*;

/** Small JSON reader/writer so the MCP core has no third-party dependency. */
final class Json {
    private Json() { }
    static Object parse(String input) { Parser parser = new Parser(input); Object v = parser.value(); parser.ws(); if (!parser.end()) throw new IllegalArgumentException("Unexpected JSON content"); return v; }
    static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return '"' + escape(s) + '"';
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) { StringJoiner j = new StringJoiner(",", "{", "}"); for (var e : map.entrySet()) j.add(stringify(String.valueOf(e.getKey())) + ':' + stringify(e.getValue())); return j.toString(); }
        if (value instanceof Iterable<?> values) { StringJoiner j = new StringJoiner(",", "[", "]"); for (Object v : values) j.add(stringify(v)); return j.toString(); }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }
    private static String escape(String s) { StringBuilder b = new StringBuilder(); for (char c : s.toCharArray()) switch (c) { case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\"); case '\b' -> b.append("\\b"); case '\f' -> b.append("\\f"); case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t"); default -> { if (c < 32) b.append(String.format("\\u%04x", (int)c)); else b.append(c); } } return b.toString(); }
    private static final class Parser {
        final String s; int i; Parser(String s) { this.s = s; } boolean end() { return i == s.length(); } void ws() { while (!end() && Character.isWhitespace(s.charAt(i))) i++; }
        Object value() { ws(); if (end()) throw new IllegalArgumentException("Empty JSON"); return switch (s.charAt(i)) { case '{' -> object(); case '[' -> array(); case '"' -> string(); case 't' -> literal("true", true); case 'f' -> literal("false", false); case 'n' -> literal("null", null); default -> number(); }; }
        Object literal(String text, Object v) { if (!s.startsWith(text, i)) throw new IllegalArgumentException("Invalid literal"); i += text.length(); return v; }
        Map<String, Object> object() { Map<String,Object> r = new LinkedHashMap<>(); i++; ws(); if (take('}')) return r; do { ws(); if (end() || s.charAt(i) != '"') throw new IllegalArgumentException("Object key expected"); String k = string(); ws(); require(':'); r.put(k, value()); ws(); } while (take(',')); require('}'); return r; }
        List<Object> array() { List<Object> r = new ArrayList<>(); i++; ws(); if (take(']')) return r; do { r.add(value()); ws(); } while (take(',')); require(']'); return r; }
        String string() { require('"'); StringBuilder b = new StringBuilder(); while (!end()) { char c=s.charAt(i++); if(c=='"') return b.toString(); if(c!='\\') { b.append(c); continue; } if(end()) break; char e=s.charAt(i++); switch(e) { case '"','\\','/' -> b.append(e); case 'b' -> b.append('\b'); case 'f' -> b.append('\f'); case 'n' -> b.append('\n'); case 'r' -> b.append('\r'); case 't' -> b.append('\t'); case 'u' -> { if(i+4>s.length()) throw new IllegalArgumentException("Bad unicode escape"); b.append((char)Integer.parseInt(s.substring(i,i+4),16)); i+=4; } default -> throw new IllegalArgumentException("Bad escape"); } } throw new IllegalArgumentException("Unterminated string"); }
        Number number() { int p=i; if(take('-')){} while(!end()&&Character.isDigit(s.charAt(i)))i++; boolean decimal=take('.'); if(decimal) while(!end()&&Character.isDigit(s.charAt(i)))i++; if(!end()&&(s.charAt(i)=='e'||s.charAt(i)=='E')) { i++; if(!end()&&(s.charAt(i)=='+'||s.charAt(i)=='-'))i++; while(!end()&&Character.isDigit(s.charAt(i)))i++; decimal=true; } try { String n=s.substring(p,i); if (decimal) return Double.parseDouble(n); return Long.parseLong(n); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid number"); } }
        boolean take(char c) { if(!end()&&s.charAt(i)==c) {i++;return true;}return false; } void require(char c) { if(!take(c)) throw new IllegalArgumentException("Expected '"+c+"'"); }
    }
}
