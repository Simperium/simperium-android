package com.simperium.client;

import java.util.ArrayList;
import java.util.List;

import android.os.CancellationSignal;

public class Query<T extends Syncable> {

    public interface Field {
        public String getName();
    }

    public static class BasicField implements Field {

        private String mName;

        public BasicField(String name) {
            mName = name;
        }

        @Override
        public String getName(){
            return mName;
        }

    }

    public static class FullTextSnippet extends BasicField {

        private String mColumnName = null;

        public FullTextSnippet(String name) {
            this(name, null);
        }

        public FullTextSnippet(String name, String columnName){
            super(name);
            mColumnName = columnName;
        }

        public String getColumnName(){
            return mColumnName;
        }

    }

    public static class FullTextOffsets extends BasicField {

        public FullTextOffsets(String name) {
            super(name);
        }

    }

    public interface Condition {
        public Object getSubject();
        public String getKey();
        public ComparisonType getComparisonType();
        public Boolean includesNull();
    }

    public enum ComparisonType {
        EQUAL_TO("="), NOT_EQUAL_TO("!=", true),
        LESS_THAN("<"), LESS_THAN_OR_EQUAL("<="),
        GREATER_THAN(">"), GREATER_THAN_OR_EQUAL(">="),
        LIKE("LIKE"), NOT_LIKE("NOT LIKE", true),
        MATCH("MATCH");

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

    public static class FullTextMatch implements Condition {

        private Object mSubject;
        private String mKey;

        public FullTextMatch(Object subject) {
            this(null, subject);
        }

        public FullTextMatch(String field, Object subject) {
            mKey = field;
            mSubject = subject;
        }

        @Override
        public Object getSubject() {
            return mSubject;
        }

        @Override
        public String getKey() {
            return mKey;
        }

        @Override
        public ComparisonType getComparisonType() {
            return ComparisonType.MATCH;
        }

        @Override
        public Boolean includesNull(){
            return false;
        }

    }

    public static class BasicCondition implements Condition {

        private String key;
        private ComparisonType type;
        private Object subject;

        public BasicCondition(String key, Query.ComparisonType type, Object subject){
            this.key = key;
            this.type = type;
            this.subject = subject;
        }

        @Override
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

        @Override
        public String toString(){
            return String.format("%s %s %s", key, type, subject);
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

    public static class KeySorter extends Sorter {
        public KeySorter(SortType type){
            super(null, type);
        }
    }

    private Bucket<T> bucket;
    private List<Condition> conditions = new ArrayList<Condition>();
    private List<Sorter> sorters = new ArrayList<Sorter>();
    private List<Field> mFields = new ArrayList<Field>();
    private int mLimit = -1;
    private int mOffset = -1;

    public Query(Bucket<T> bucket){
        this.bucket = bucket;
    }

    public Query(){
        bucket = null;
    }

    public boolean hasLimit(){
        return mLimit != -1;
    }

    public int getLimit(){
        return mLimit;
    }

    public Query limit(int limit) {
        mLimit = limit;
        return this;
    }

    public Query clearLimit() {
        return limit(-1);
    }

    public boolean hasOffset(){
        return mOffset != -1;
    }

    public int getOffset(){
        return mOffset;
    }

    public Query offset(int offset) {
        mOffset = offset;
        return this;
    }

    public Query clearOffset() {
        return offset(-1);
    }

    public Query where(Condition condition){
        conditions.add(condition);
        return this;
    }

    public Query where(String key, ComparisonType type, Object subject){
        where(new BasicCondition(key, type, subject));
        return this;
    }

    public List<Condition> getConditions(){
        return conditions;
    }

    public List<Sorter> getSorters(){
        return sorters;
    }

    public List<Field> getFields(){
        return mFields;
    }

    public Bucket.ObjectCursor<T> execute(){
        if (bucket == null) {
            throw(new RuntimeException("Tried executing a query without a bucket"));
        }
        return bucket.searchObjects(this);
    }

    public int count(){
        if (bucket == null){
            throw(new RuntimeException("Tried executing a query wihtout a bucket"));
        }
        return bucket.count(this);
    }

    public Query orderByKey(){
        orderByKey(SortType.ASCENDING);
        return this;
    }
    public Query orderByKey(SortType type){
        order(new KeySorter(type));
        return this;
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

    public Query reorder(){
        sorters.clear();
        return this;
    }

    public Query include(String key){
        // we want to include some indexed values
        mFields.add(new BasicField(key));
        return this;
    }

    public Query include(String ... keys){
        for (String key : keys) {
            mFields.add(new BasicField(key));
        }
        return this;
    }

    public Query include(Field field){
        mFields.add(field);
        return this;
    }

    public Query include(Field ... fields){
        for(Field field : fields){
            mFields.add(field);
        }
        return this;
    }

}