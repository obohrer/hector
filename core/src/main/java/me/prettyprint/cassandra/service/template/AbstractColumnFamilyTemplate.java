package me.prettyprint.cassandra.service.template;

import java.util.HashMap;
import java.util.Map;

import me.prettyprint.cassandra.model.HSlicePredicate;
import me.prettyprint.cassandra.service.ExceptionsTranslator;
import me.prettyprint.cassandra.service.ExceptionsTranslatorImpl;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.Serializer;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.mutation.MutationResult;
import me.prettyprint.hector.api.mutation.Mutator;

import org.apache.cassandra.thrift.ColumnParent;

public class AbstractColumnFamilyTemplate<K, N> {
  // Used for queries where we just ask for all columns
  public static final int ALL_COLUMNS_COUNT = Integer.MAX_VALUE;
  public static final Object ALL_COLUMNS_START = null;
  public static final Object ALL_COLUMNS_END = null;

  protected Keyspace keyspace;
  protected String columnFamily;
  protected Serializer<K> keySerializer;
  protected Map<N, Serializer<?>> columnValueSerializers;
  protected ColumnParent columnParent;
  protected HSlicePredicate<N> activeSlicePredicate;
  
  /** The serializer for a standard column name or a super-column name */
  protected Serializer<N> topSerializer;

  /**
   * Used for all updates. Can but passed in the constructor/reassigned to allow
   * updates between multiple column families/CassandraTemplates to be batched
   */
  protected Mutator<K> mutator;

  /**
   * By default, execute updates automatically at common-sense points such as
   * after queuing the updates of all an object's properties. Or, in the case of
   * multiple objects, at the end of the list. No Mutator executes() will be
   * called if this is set to true. This allows an arbitrary number of updates
   * to be performed and executed manually.
   */
  protected boolean batched;

  /**
   * An optional clock value to pass to deletes. If null, the default value
   * generated by Hector is used
   */
  protected Long clock;
  
  protected ExceptionsTranslator exceptionsTranslator;

  public AbstractColumnFamilyTemplate(Keyspace keyspace, String columnFamily,
      Serializer<K> keySerializer, Serializer<N> topSerializer) {
    this(keyspace, columnFamily, keySerializer, topSerializer, HFactory
        .createMutator(keyspace, keySerializer));
  }

  public AbstractColumnFamilyTemplate(Keyspace keyspace, String columnFamily,
      Serializer<K> keySerializer, Serializer<N> topSerializer,
      Mutator<K> mutator) {
    // ugly, but safe
    this.keyspace = keyspace;
    this.columnFamily = columnFamily;
    this.keySerializer = keySerializer;
    this.topSerializer = topSerializer;
    this.mutator = mutator;
    columnValueSerializers = new HashMap<N, Serializer<?>>();
    this.columnParent = new ColumnParent(columnFamily);
    this.activeSlicePredicate = new HSlicePredicate<N>(topSerializer);
    exceptionsTranslator = new ExceptionsTranslatorImpl();
  }


  /**
   * Add a column to the static set of columns which will be used in constructing 
   * the single-argument form of slicing operations
   * @param columnName
   * @param valueSerializer
   */
  public AbstractColumnFamilyTemplate<K,N> addColumn(N columnName, Serializer<?> valueSerializer) {
    columnValueSerializers.put(columnName, valueSerializer);
    activeSlicePredicate.addColumnName(columnName);
    return this;
  }
  
  /**
   * Get the value serializer for a given column. Returns null if none found
   * @param columnName
   * @return
   */
  public Serializer<?> getValueSerializer(N columnName) {
    return columnValueSerializers.get(columnName);
  }
   
  
  public boolean isBatched() {
    return batched;
  }

  public AbstractColumnFamilyTemplate<K, N> setBatched(boolean batched) {
    this.batched = batched;
    return this;
  }

  public String getColumnFamily() {
    return columnFamily;
  }

  public Serializer<K> getKeySerializer() {
    return keySerializer;
  }

  public Serializer<N> getTopSerializer() {
    return topSerializer;
  }

  public MutationResult executeBatch() {    
    MutationResult result = mutator.execute();
    mutator.discardPendingMutations();
    return result;
  }

  public Mutator<K> getMutator() {
    return mutator;
  }

  public AbstractColumnFamilyTemplate<K, N> setMutator(Mutator<K> mutator) {
    this.mutator = mutator;
    return this;
  }

  public Long getClock() {
    return clock;
  }

  public void setClock(Long clock) {
    this.clock = clock;
  }

  public long getEffectiveClock() {
    return clock != null ? clock.longValue() : keyspace.createClock();
  }  

  public void setExceptionsTranslator(ExceptionsTranslator exceptionsTranslator) {
    this.exceptionsTranslator = exceptionsTranslator;
  }

  protected MutationResult executeIfNotBatched() {    
    return !isBatched() ? executeBatch() : null;
  }

  public void deleteRow(K key) {
    mutator.addDeletion(key, columnFamily, null, topSerializer);
    executeIfNotBatched();
  }

  public void deleteColumn(K key, N columnName) {
    mutator.addDeletion(key, columnFamily, columnName, topSerializer);
    executeIfNotBatched();
  }

}
