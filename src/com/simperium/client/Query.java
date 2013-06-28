package com.simperium.client;

import java.util.ArrayList;
import java.util.List;

public class Query<T extends Syncable> {
    
    private List<Comparator> conditions = new ArrayList<Comparator>();

    public interface Comparator {
        public Object getSubject();
        public String getKey();
        public ComparisonType getComparisonType();
        public Boolean includesNull();
    }

    public enum ComparisonType {
        EQUAL_TO("="), NOT_EQUAL_TO("!=", true),
        LESS_THAN("<"), LESS_THAN_OR_EQUAL("<="),
        GREATER_THAN(">"), GREATER_THAN_OR_EQUAL(">="),
        LIKE("LIKE"), NOT_LIKE("NOT LIKE", true);

        private final String operator;
        private final Boolean includesNull;

        private ComparisonType(String operator){
            this(operator, false);
        }

        private ComparisonType(String operator, Boolean includesNull){
            this.operator = operator;
            this.includesNull = includesNull;
        }

        public Boolean includesNull(){
            return includesNull;
        }

        @Override
        public String toString(){
            return operator;
        }
    }

    public static class BasicComparator implements Comparator {

        private String key;
        private ComparisonType type;
        private Object subject;

        public BasicComparator(String key, Query.ComparisonType type, Object subject){
            this.key = key;
            this.type = type;
            this.subject = subject;
        }

        public Object getSubject(){
            return subject;
        }

        @Override
        public String getKey(){
            return key;
        }

        @Override
        public ComparisonType getComparisonType(){
            return type;
        }

        @Override
        public Boolean includesNull(){
            return type.includesNull();
        }
    }

    public interface Sorter {
        
    }

    private Bucket<T> bucket;

    public Query(Bucket<T> bucket){
        this.bucket = bucket;
    }

    public Query(){
        bucket = null;
    }

    public Query where(Comparator condition){
        conditions.add(condition);
        return this;
    }

    public Query where(String key, ComparisonType type, Object subject){
        where(new BasicComparator(key, type, subject));
        return this;
    }

    public List<Comparator> getConditions(){
        return conditions;
    }

    public Bucket.ObjectCursor<T> execute(){
        if (bucket == null) {
            return null;
        }
        return bucket.searchObjects(this);
    }
}