package com.simperium.client;

import java.util.ArrayList;
import java.util.List;

public class Query {
    
    private List<Comparator> conditions = new ArrayList<Comparator>();

    public interface Comparator {
        
    }

    public static class BasicComparator implements Comparator {

        public enum ComparisonType {
            EQUALS,
            LESS_THAN, LESS_THAN_OR_EQUAL,
            GREATER_THAN, GREATER_THAN_OR_EQUAL,
            MATCHES
        }

        private String key;
        private ComparisonType type;
        private Object subject;

        public BasicComparator(String key, ComparisonType type, Object subject){
            this.key = key;
            this.type = type;
            this.subject = subject;
        }
    }

    public interface Sorter {
        
    }

    public Query where(Comparator condition){
        conditions.add(condition);
        return this;
    }

    public Query where(String key, BasicComparator.ComparisonType type, Object subject){
        where(new BasicComparator(key, type, subject));
        return this;
    }
}