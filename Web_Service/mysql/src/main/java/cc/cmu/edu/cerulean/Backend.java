package cc.cmu.edu.cerulean;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.LinkedHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.xml.bind.DatatypeConverter;

public class Backend extends HttpServlet {
    // Stopwords
    private final String PATH = "src/stopwords.txt";
    private Set<String> stopWords = new HashSet<String>();
    
    // mysql connections
    private static final long serialVersionUID = 1L;
    private final String JDBCDriver = "com.mysql.jdbc.Driver";
    private final String URL = "jdbc:mysql:///twitter?useSSL=false&characterEncoding=UTF-8&useUnicode=true";
    private String DBUSER = System.getenv("USERNAME");
    private String DBPWD = System.getenv("PASSWORD"); 
    private HikariDataSource ds;
    private Map<String, String> lruCache;
    
    public Backend() {     
        // initialize stopwords
        Path path = Paths.get(PATH);
        Charset charset = Charset.forName("UTF-8");
        try {
            BufferedReader reader = Files.newBufferedReader(path, charset);
            String word = null;
            while ((word = reader.readLine()) != null) {
                stopWords.add(word);
            }
        } catch (IOException e) {
            System.out.println("IOException");
            System.out.println(e);
        }

        // initialize MySQL database connection using HikariCP
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(DBUSER);
        config.setPassword(DBPWD);
        config.addDataSourceProperty("maximumPoolSize", "500");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        ds = new HikariDataSource(config);

        // initialize LRU Cache
        lruCache = new LimitedLinkedHashMap<String, String>(1200000);
    }    

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) 
            throws ServletException, IOException {
        // Query 
        String sql = "SELECT uid, hashtags, count FROM twitter_tags WHERE keyword = ?";
        String result = "Cerulean\n";
        Long startTime = System.currentTimeMillis();
        // Parameters
        String keywords = request.getParameter("keywords");
        String n = request.getParameter("n");
        String user_id = request.getParameter("user_id");
        
        // Malform requests
        if (keywords == null || n == null || user_id == null || 
            keywords.length() == 0 || n.length() == 0 || user_id.length() == 0) {
            PrintWriter writer = response.getWriter();
            writer.write(result);
            writer.close();
        }
        
        // if n is not an integer
        int num = 0;
        try {
            num = Integer.parseInt(n);
        } catch (NumberFormatException e) {
            PrintWriter writer = response.getWriter();
            writer.write(result);
            writer.close();
        }

        /**
         * Cache: GET
         */
        // Generate digest
        String concat = user_id + keywords + n;
        String digest = "";
        MessageDigest msgDigest;
        try {
            msgDigest = MessageDigest.getInstance("MD5");
            byte[] md5 = msgDigest.digest(concat.getBytes(StandardCharsets.UTF_8));
            digest = DatatypeConverter.printHexBinary(md5);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        if (lruCache.containsKey(digest)) {
            String hashtags = lruCache.get(digest);
			if (hashtags != null) {
                response.setContentType("text/plain; charset=UTF-8");
				PrintWriter writer = response.getWriter();
                writer.write(result);
				writer.write(hashtags);
				writer.close();
				return;
		    }
        }

        // Check one word or more than one
        String[] keywordsArr = null;        
        if (!keywords.contains(",")) {
            // if only have one keyword
            keywordsArr = new String[]{keywords};
        } else {
            keywordsArr = keywords.split(",");
        }
        
        // Hashtable and Hashset
        Map<String, Long> hashtagToCount = new HashMap<>();
        for (int i = 0; i < keywordsArr.length; i++) {
            String inputKeyword = keywordsArr[i];
            // Get rid of stopwords
            if (stopWords.contains(inputKeyword)) {
                continue;
            }           
            
            try (Connection mysqlConn = ds.getConnection()){
                PreparedStatement pStatement = mysqlConn.prepareStatement(sql);
                pStatement.setString(1, inputKeyword);
                ResultSet rSet = pStatement.executeQuery();
                while (rSet.next()) {
                    String curUid = rSet.getString("uid");
                    boolean isUid = curUid.equals(user_id);
                    long curCount = rSet.getLong("count");
                    String curHashtags = rSet.getString("hashtags");
                    String[] tagsArr = curHashtags.split(",");
                    // Get tag
                    for (int j = 0; j < tagsArr.length; j++) {
                        String curHashTag = tagsArr[j];
                        if (!hashtagToCount.containsKey(curHashTag)) {
                            if (isUid) {
                                hashtagToCount.put(curHashTag, 2 * curCount);
                            } else {
                                hashtagToCount.put(curHashTag, curCount);
                            }                    
                        } else {
                            if (isUid) {
                                hashtagToCount.replace(curHashTag, hashtagToCount.get(curHashTag) + 2 * curCount);
                            } else {
                                hashtagToCount.replace(curHashTag, hashtagToCount.get(curHashTag) + curCount);
                            }                    
                        }
                    }
                }          

            } catch (SQLException e) {
                System.out.println(e);
                System.out.println("SQL Exception");
            }           
        }

        // No hashtag
        if (hashtagToCount.size() == 0) {
            PrintWriter writer = response.getWriter();
            writer.write(result);
            writer.close();
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
        
        /**
         * Cache: PUT
         */
        synchronized(lruCache) {
            lruCache.put(digest, hashtags.toString());
        }
        // Set encoding to UTF-8 
        response.setContentType("text/plain; charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.write(result);
        writer.write(hashtags.toString());
        writer.close();
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
    
    private static class HashTag {
        String hashtag;
        long count;
        
        public HashTag(String hashtag, long count) {
            this.hashtag = hashtag;
            this.count = count;
        }
    }

    private static class LimitedLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
        int capacity;

        public LimitedLinkedHashMap(int capacity) {
            super(capacity, 0.75f, true);
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > capacity;
        }
    }
}
