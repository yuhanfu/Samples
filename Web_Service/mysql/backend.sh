# install mysql
sudo apt-get update
sudo apt-get install mysql-server # need specify password

# install python
sudo apt-get install python -y

# install gutil
curl https://sdk.cloud.google.com | bash # multiple yes

## mysql
# create table from mysql
CREATE TABLE twitter_test (
    keyword varchar(255) NOT NULL,
    uid varchar(255) NOT NULL,
    hashtags varchar(1024) NOT NULL,
    count BIGINT(8) NOT NULL
);

# doanload file
sudo gsutil cp gs://teamprojectcc/twitter/mapreduce2/* ./

# import data
LOAD DATA LOCAL INFILE 'part-00000' INTO TABLE twitter_test 
FIELDS TERMINATED BY '\t'

## shell 
mysqlimport --fields-terminated-by='\t' \
            --local -u root -p password\
            twitter \
            twitter_test.tsv

# create index on keyword column
create index keyword_idx on twitter_test (keyword)

# check database size
SELECT table_schema "Data Base Name", 
sum( data_length + index_length) / 1024 / 1024 "Data Base Size in MB" 
FROM information_schema.TABLES GROUP BY table_schema;

