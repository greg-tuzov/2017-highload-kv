package ru.mail.polis.gt;

import java.util.LinkedHashMap;
import java.util.Map;

public class LFUCache {

    class CacheEntry {

        private byte[] data;
        private int frequency;

        public byte[] getData() {
            return data;
        }
        public void setData(byte[] data) {
            this.data = data;
        }
        public int getFrequency() {
            return frequency;
        }
        public void setFrequency(int frequency) {
            this.frequency = frequency;
        }
    }

    private int initialCapacity = 100;
    private LinkedHashMap<String, CacheEntry> cacheMap = new LinkedHashMap<String, CacheEntry>();

    public LFUCache(int initialCapacity)  {
        this.initialCapacity = initialCapacity;
    }

    public void addCacheEntry(String key, byte[] data)  {
        if(!isFull())  {
            CacheEntry temp = new CacheEntry();
            temp.setData(data);
            temp.setFrequency(0);

            cacheMap.put(key, temp);
        }  else  {
            String entryKeyToBeRemoved = getLFUKey();
            cacheMap.remove(entryKeyToBeRemoved);

            CacheEntry temp = new CacheEntry();
            temp.setData(data);
            temp.setFrequency(0);

            cacheMap.put(key, temp);
        }
    }

    public String getLFUKey()  {
        String key = "";
        int minFreq = Integer.MAX_VALUE;

        for(Map.Entry<String, CacheEntry> entry : cacheMap.entrySet())  {
            if(minFreq > entry.getValue().frequency)  {
                key = entry.getKey();
                minFreq = entry.getValue().frequency;
            }
        }
        return key;
    }

    public byte[] getCacheEntry(String key)  {
        if(cacheMap.containsKey(key))  {
            CacheEntry temp = cacheMap.get(key);
            temp.frequency++;
            cacheMap.put(key, temp);
            return temp.data;
        }
        return null;
    }

    public void rm(String key) {
        cacheMap.remove(key);
    }

    public void empty() {
        cacheMap = new LinkedHashMap<String, CacheEntry>();
    }

    public boolean isFull()  {
        if(cacheMap.size() == initialCapacity)
            return true;

        return false;
    }
}