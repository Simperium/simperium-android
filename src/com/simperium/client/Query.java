package com.simperium.client;

import java.util.ArrayList;
import java.util.List;

public class Query<T extends Syncable> {

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

    public enum SortType {

        ASCENDING("ASC"), DESCENDING("DESC");

        private final String type;

        private SortType(String type){
            this.type = type;
        }

        public String getType(){
            return type;
        }

        @Override
        public String toString(){
            return getType();
        }
    }

    public static class Sorter {

        private final String key;
        private final SortType type;

        public Sorter(String key, SortType type){
            this.key = key;
            this.type = type;
        }

        public String getKey(){
            return key;
        }

        public SortType getType(){
            return type;
        }
    }

    private Bucket<T> bucket;
    private List<Comparator> conditions = new ArrayList<Comparator>();
    private List<Sorter> sorters = new ArrayList<Sorter>();

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

    public List<Sorter> getSorters(){
        return sorters;
    }

    public Bucket.ObjectCursor<T> execute(){
        if (bucket == null) {
            return null;
        }
        return bucket.searchObjects(this);
    }

    public Query order(Sorter sort){
        sorters.add(sort);
        return this;
    }

    public Query order(String key){
        order(key, SortType.ASCENDING);
        return this;
    }

    public Query order(String key, SortType type){
        order(new Sorter(key, type));
        return this;
    }
}