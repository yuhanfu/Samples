package edu.cmu.cc.web.frontend;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.Collections;
import java.util.PriorityQueue;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Comparator;
import java.util.Objects;

import io.vertx.core.VertxOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Scan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.xml.bind.DatatypeConverter;

import org.json.JSONObject;

public class Backend extends AbstractVerticle {

    /**
     * The private IP address of HBase master node.
     */
    private static String zkAddr = "172.31.48.255";
    /**
     * The name of HBase table.
     */
    private static TableName tableName2 = TableName.valueOf("q2");

    private static TableName tableName3 = TableName.valueOf("q3");
    /**
     * HTable handler.
     */
    private static Table tagTable;

    private static Table twiTable;

    /**
     * HBase connection.
     */
    private static Connection conn;
    /**
     * Byte representation of column family and columns.
     */
    private final static byte[] ColF = Bytes.toBytes("u");

    private final static byte[] Col = Bytes.toBytes("ut");

    private final static byte[] twiColF = Bytes.toBytes("t");
    
    private final static byte[] twiCol = Bytes.toBytes("twi");
    /**
    * Stopwords.
    */
    private final String PATH1 = "src/stopwords.txt";

    private final String PATH2 = "src/decoded-censored-words.txt";

    private Set<String> stopWords = new HashSet<String>();

    private Set<String> censoredWords = new HashSet<String>();

    //private Map<String, String> lruCache;
	
    public static void main(String[] args) {

        DeploymentOptions options = new DeploymentOptions();
        options.setInstances(Runtime.getRuntime().availableProcessors());
        Vertx.vertx().deployVerticle(Backend.class, options);
	}

	@Override
	public void start() {
        Long initial = System.currentTimeMillis();
        initialization();
        Long initial2 = System.currentTimeMillis();
        Long interval2 = initial2 - initial;
        System.out.println("initial time: " + String.valueOf(interval2) + "\n");
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.get("/q2").handler(this::handleTag);
        router.get("/q3").handler(this::handleTwitter);

        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
    }

    /**
     * Initialize HBase connection.
     *
     */
    private void initialization() {

        try {
            if (!zkAddr.matches("\\d+.\\d+.\\d+.\\d+")) {
                System.out.print("Malformed HBase IP address");
                System.exit(-1);
            }
            Configuration conf = HBaseConfiguration.create();
            conf.set("hbase.master", zkAddr + ":14000");
            conf.set("hbase.zookeeper.quorum", zkAddr);
            conf.set("hbase.zookeeper.property.clientport", "2181");
            conn = ConnectionFactory.createConnection(conf);
            tagTable = conn.getTable(tableName2);
            twiTable = conn.getTable(tableName3);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // initialize stopwords
        Path path1 = Paths.get(PATH1);
        Charset charset = Charset.forName("UTF-8");
        try {
            BufferedReader reader = Files.newBufferedReader(path1, charset);
            String word = null;
            while ((word = reader.readLine()) != null) {
                stopWords.add(word);
            }
        } catch (IOException e) {
            System.out.println("IOException");
            System.out.println(e);
        }

        // initialize censor words
        Path path2 = Paths.get(PATH2);
        try {
            BufferedReader reader = Files.newBufferedReader(path2, charset);
            String word = null;
            while ((word = reader.readLine()) != null) {
                censoredWords.add(word);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void handleTag(RoutingContext routingContext) {
        String keywords = routingContext.request().getParam("keywords");
        String n = routingContext.request().getParam("n");
        String user_id = routingContext.request().getParam("user_id");
        HttpServerResponse response = routingContext.response();

        String result = "Cerulean\n";
        System.out.println(Thread.currentThread().getName());

        // Malform requests
        if (keywords == null || n == null || user_id == null || 
            keywords.length() == 0 || n.length() == 0 || user_id.length() == 0) {
            response.putHeader("content-type", "text/plain").end(result);
            return;
        }

        // if n is not an integer
        int num = 0;
        try {
            num = Integer.parseInt(n);
        } catch (NumberFormatException e) {
            response.putHeader("content-type", "text/plain").end(result);
            return;
        }

        // Check one word or more than one
        String[] keywordsArr = null;        
        if (!keywords.contains(",")) {
            // if only have one keyword
            keywordsArr = new String[]{keywords};
        } else {
            keywordsArr = keywords.split(",");
        }

        Map<String, Long> hashtagToCount = new HashMap<>();
        List<Get> getList = new ArrayList<>();
        for (int i = 0; i < keywordsArr.length; i++) {
            String inputKeyword = keywordsArr[i];
            if (stopWords.contains(inputKeyword)) {
                continue;
            }
            String hashKey = hashHelper(inputKeyword);
            Get getRow = new Get(Bytes.toBytes(hashKey));
            getList.add(getRow);
        }
        Result[] results = null;
        try {
            results = tagTable.get(getList);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        for (int i = 0; i < results.length; i++) {
            Result r = results[i];
            byte[] v = r.getValue(ColF, Col);
            JSONObject curDataObject = new JSONObject(Bytes.toString(v));
            JSONObject allTagObject = curDataObject.getJSONObject("alltag");
            JSONObject userObject = curDataObject.getJSONObject("user");
            // Iterate over all tags
            for (String tag : allTagObject.keySet()) {
                long curCount = allTagObject.getLong(tag);
                if (!hashtagToCount.containsKey(tag)) {
                    hashtagToCount.put(tag, curCount);
                } else {
                    hashtagToCount.put(tag, hashtagToCount.get(tag) + curCount);
                }                    
            }
            Set<String> userIdSet = userObject.keySet();
            if (userIdSet.contains(user_id)) {
                // add to corresponding hashtag
                JSONObject uidTagsObject = userObject.getJSONObject(user_id);
                Set<String> userTags  = uidTagsObject.keySet();
                for (String userTag : userTags) {
                    long curUserTagCount = uidTagsObject.getLong(userTag);
                    hashtagToCount.put(userTag, hashtagToCount.get(userTag) + curUserTagCount);
                }
            }
        }

        // No hashtag
        if (hashtagToCount.size() == 0) {
            response.putHeader("content-type", "text/plain").end(result);
            return;
        }

        // Get the first n hashtag: PriorityQueue
        PriorityQueue<HashTag> hashtagQueue = new PriorityQueue<>(new Comparator<HashTag>() {
            @Override
            public int compare(HashTag h1, HashTag h2) {
                int result = Long.compare(h1.count, h2.count);
                if (result == 0) {
                    return h1.hashtag.compareTo(h2.hashtag);
                }
                return -result;
            }            
        });
        for (Map.Entry<String, Long> hashtagEntry : hashtagToCount.entrySet()) {
            HashTag hashtag = new HashTag(hashtagEntry.getKey(), hashtagEntry.getValue());
            hashtagQueue.offer(hashtag);
        }

        StringBuilder hashtags = new StringBuilder();
        if (hashtagQueue.size() < num) {
            int queueSize = hashtagQueue.size();
            for (int i = 0; i < queueSize; i++) {
                HashTag curHashTag = hashtagQueue.poll();          
                if (i == queueSize - 1) {
                    hashtags.append("#").append(curHashTag.hashtag).append("\n");
                } else {
                    hashtags.append("#").append(curHashTag.hashtag).append(",");
                }
            }
        } else {
            for (int i = 0; i < num; i++) {
                HashTag curHashTag = hashtagQueue.poll();          
                if (i == num - 1) {
                    hashtags.append("#").append(curHashTag.hashtag).append("\n");
                } else {
                    hashtags.append("#").append(curHashTag.hashtag).append(",");
                }
            }
        }
        
        String hashResult = result + hashtags.toString();
        response.putHeader("content-type", "text/plain").end(hashResult);
    }

    private String hashHelper(String key) {
        String digest = "";
        MessageDigest msgDigest;
        try {
            msgDigest = MessageDigest.getInstance("MD5");
            byte[] md5 = msgDigest.digest(key.getBytes(StandardCharsets.UTF_8));
            digest = DatatypeConverter.printHexBinary(md5);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return digest;
    }

    private static class HashTag {
        String hashtag;
        long count;
        
        public HashTag(String hashtag, long count) {
            this.hashtag = hashtag;
            this.count = count;
        }
    }

    private void handleTwitter(RoutingContext routingContext) {
        String timeStart = routingContext.request().getParam("time_start");
        String timeEnd = routingContext.request().getParam("time_end");
        String userStart = routingContext.request().getParam("uid_start");
        String userEnd = routingContext.request().getParam("uid_end");
        String n1 = routingContext.request().getParam("n1");
        String n2 = routingContext.request().getParam("n2");
        HttpServerResponse response = routingContext.response();

        String result = "Cerulean\n";

        if (timeStart == null || timeEnd == null || userStart == null || userEnd == null || n1 == null || n2 == null ||
            timeStart.length() == 0 || timeEnd.length() == 0 || n1.length() == 0 || n2.length() == 0 || userStart.length() == 0 || userEnd.length() == 0) {
            response.putHeader("content-type", "text/plain").end(result);
            return;
        }

        // if n is not an integer
        int numberOfTopicWords = 0;
        try {
            numberOfTopicWords = Integer.parseInt(n1);
        } catch (NumberFormatException e) {
            response.putHeader("content-type", "text/plain").end(result);
            return;
        }
        int numberOfTweets= 0;
        try {
            numberOfTweets = Integer.parseInt(n2);
        } catch (NumberFormatException e) {
            response.putHeader("content-type", "text/plain").end(result);
            return;
        }

        long user1 = 0;
        long user2 = 0;
        long time1 = 0;
        long time2 = 0;
        try {
            user1 = Long.parseLong(userStart);
            user2 = Long.parseLong(userEnd);
            time1 = Long.parseLong(timeStart);
            time2 = Long.parseLong(timeEnd);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        if (user2 - user1 > 200000) {
            response.putHeader("content-type", "text/plain").end(result);
            return;
        }

        // Get rows that have user ID between user1 and user2
        Scan scan = new Scan();
        scan.addFamily(twiColF);
        scan.withStartRow(Bytes.toBytes(user1), true);
        scan.withStopRow(Bytes.toBytes(user2), true);
        ResultScanner rs = null;
        try {
            rs = twiTable.getScanner(scan);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        Map<String, List<TweetInfo>> wordToTweetInfo = new HashMap<>();
        int numOfTweets = 0;
        try {
            for (Result r = rs.next(); r != null; r = rs.next()) {
                byte[] v = r.getValue(twiColF, twiCol);
                JSONObject times = new JSONObject(Bytes.toString(v));
                Iterator<String> itr = times.keys();

                while (itr.hasNext()){
                    String timestamp = itr.next();
                    if (!inRange(timestamp, time1, time2)) {
                        continue;
                    }
                    numOfTweets++;
                    JSONObject infoObject = times.getJSONObject(timestamp);
                    JSONObject wordsTFObject = infoObject.getJSONObject("TF");
                    long curImportScore = infoObject.getLong("impact");
                    long curTweetId = infoObject.getLong("tid");
                    int length = infoObject.getInt("length");
                    String curText = infoObject.getString("ttext");
                    Iterator<String> iterator = wordsTFObject.keys();
                    while (iterator.hasNext()) {
                        String word = iterator.next();
                        int numOfWord = wordsTFObject.getInt(word);
                        double tf = (double) numOfWord / length;
                        // Add to word and tf, impact score, text object
                        TweetInfo curTweetInfo = new TweetInfo(curTweetId, tf, curImportScore, curText);
                        if (wordToTweetInfo.containsKey(word)) {
                            wordToTweetInfo.get(word).add(curTweetInfo);                     
                        } else {
                            List<TweetInfo> tweetInfoList = new ArrayList<>();
                            tweetInfoList.add(curTweetInfo);
                            wordToTweetInfo.put(word, tweetInfoList);
                        }
                    }
            
                }

            }
            rs.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // No topic words
        if (wordToTweetInfo.size() == 0) {
            response.putHeader("content-type", "text/plain").end(result);
            return;
        }

        // Find top n1 topic words
        PriorityQueue<TopicWord> topicWordQueue = new PriorityQueue<>(new Comparator<TopicWord>() {
            @Override
            public int compare(TopicWord t1, TopicWord t2) {
                int result = Double.compare(t1.score, t2.score);
                if (result == 0) {
                    return t1.word.compareTo(t2.word);
                }
                return -result;
            }            
        });
        for (Map.Entry<String, List<TweetInfo>> entry : wordToTweetInfo.entrySet()) {
            String word = entry.getKey();
            List<TweetInfo> tweetInfos = entry.getValue();
            long numOfTweetsContainWord = tweetInfos.size();
            double curScore = 0.0;
            for (TweetInfo cur : tweetInfos) {
                double td_idf = cur.tf * Math.log((double) numOfTweets / numOfTweetsContainWord);
                
                curScore += td_idf * Math.log(cur.impactScore + 1);
            }
            TopicWord curTopicWord = new TopicWord(word, curScore);
            topicWordQueue.offer(curTopicWord);
        }

        // Find top n2 tweets contain topic words
        PriorityQueue<TweetInfo> tweetInfoQueue = new PriorityQueue<>(new Comparator<TweetInfo>() {
            @Override
            public int compare(TweetInfo t1, TweetInfo t2) {
                int result = Long.compare(t1.impactScore, t2.impactScore);
                if (result == 0) {
                    return -Long.compare(t1.tweetId, t2.tweetId);
                }
                return -result;
            }            
        });
        // Get the first n1 topic words and add tweets to tweet info queue
        StringBuilder topicWords = null;
        if (topicWordQueue.size() < numberOfTopicWords) {
            int queueSize = topicWordQueue.size();
            topicWords = constructTopicWordResult(topicWordQueue, tweetInfoQueue, wordToTweetInfo, queueSize);
        } else {
            topicWords = constructTopicWordResult(topicWordQueue, tweetInfoQueue, wordToTweetInfo, numberOfTopicWords);
        }

        // Construct tweets result
        StringBuilder tweets = null;
        if (tweetInfoQueue.size() < numberOfTweets) {
            int queueSize = tweetInfoQueue.size();
            tweets = constructTweetsResult(tweetInfoQueue, queueSize);
        } else {
            tweets = constructTweetsResult(tweetInfoQueue, numberOfTweets);
        }
        String finalResult = result + topicWords.toString() + tweets.toString();
        response.putHeader("content-type", "text/plain").end(finalResult);
    }

    private StringBuilder constructTopicWordResult(PriorityQueue<TopicWord> topicWordQueue, 
                                                   PriorityQueue<TweetInfo> tweetInfoQueue, 
                                                   Map<String, List<TweetInfo>> wordToTweetInfo,
                                                   int num) {
        StringBuilder topicWords = new StringBuilder();
        Set<TweetInfo> tweetInfoSet = new HashSet<>();
        for (int i = 0; i < num; i++) {
            TopicWord curTopicWord = topicWordQueue.poll();
            String curWord =  curTopicWord.word;
            // Censor topic words
            if (censoredWords.contains(curWord)) {
                char[] curWordArr = curWord.toCharArray();
                Arrays.fill(curWordArr, '*');
                curWordArr[0] = curWord.charAt(0);
                curWordArr[curWordArr.length - 1] = curWord.charAt(curWordArr.length - 1);
                curWord = new String(curWordArr);
            }  
            // Add all current topic word related tweets to tweetInfoQueue
            List<TweetInfo> tweetsContainCurTopicWord = wordToTweetInfo.get(curTopicWord.word);
            // remove duplicates 
            tweetInfoSet.addAll(tweetsContainCurTopicWord);      
            if (i == num - 1) {
                topicWords.append(curWord).append(":")
                          .append(String.format("%.2f", curTopicWord.score)).append("\n");
                continue;
            } 
            topicWords.append(curWord).append(":")
                      .append(String.format("%.2f", curTopicWord.score)).append("\t");
        }
        tweetInfoQueue.addAll(tweetInfoSet);

        return topicWords;
    }

    private StringBuilder constructTweetsResult(PriorityQueue<TweetInfo> tweetInfoQueue, int num) {
        StringBuilder tweets = new StringBuilder();
        for (int i = 0; i < num; i++) {
            TweetInfo curTweetInfo = tweetInfoQueue.poll(); 
            if (i == num - 1) {
                tweets.append(curTweetInfo.impactScore).append("\t")
                .append(curTweetInfo.tweetId).append("\t")
                .append(curTweetInfo.text);
                continue;                
            }
            tweets.append(curTweetInfo.impactScore).append("\t")
                  .append(curTweetInfo.tweetId).append("\t")
                  .append(curTweetInfo.text).append("\n");
        }   
        return tweets;                                                 
    }

    private boolean inRange(String time, long start, long end) {
        long t = Long.parseLong(time);
        if (t >= start && t <= end) {
            return true;
        } else {
            return false;
        }
    }

    private static class TweetInfo {
        long tweetId;
        double tf;
        long impactScore;
        String text;

        public TweetInfo (long tweetId, double tf, long impactScore, String text) {
            this.tweetId = tweetId;
            this.tf = tf;
            this.impactScore = impactScore;
            this.text = text;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tweetId);
        }

        @Override
        public boolean equals(Object obj) {            
            TweetInfo info = (TweetInfo) obj;
            return Objects.equals(tweetId, info.tweetId);
        }
    }

    private static class TopicWord {        
        String word;
        double score;

        public TopicWord (String word, double score) {
            this.word = word;
            this.score = score;
        }
    }

}