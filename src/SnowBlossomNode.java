package snowblossom;

import io.grpc.ServerBuilder;
import io.grpc.Server;

import snowblossom.db.DB;
import snowblossom.trie.HashedTrie;
import snowblossom.trie.TrieDBRocks;

import java.security.Security;


import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;


public class SnowBlossomNode
{
  private static final Logger logger = Logger.getLogger("SnowBlossomNode");
  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();

    if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: SnowBlossomNode <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);

    new SnowBlossomNode(config);
  }

  private Config config;
  private SnowUserService user_service;
  private DB db;
  private NetworkParams params;
  private BlockIngestor ingestor;
  private BlockForge forge;
  private HashedTrie utxo_hashed_trie;

  public SnowBlossomNode(Config config)
    throws Exception
  {
    this.config = config;

    config.require("db_type");

    setupParams();
    loadDB();
    loadUtxoDB();
    loadWidgets();
    startServices();

    while(true)
    {
      Thread.sleep(10000);
      if (user_service!=null)
      {
        user_service.tickleBlocks();
      }
    }
    //s.awaitTermination();

  }

  private void setupParams()
  {
    if (config.isSet("network"))
    {
      String network = config.get("network");

      if (network.equals("snowblossom"))
      {
        params = new NetworkParamsProd();
      }
      else if (network.equals("teapot"))
      {
        logger.info("Using network teapot - testnet");
        params = new NetworkParamsTestnet();
      }
      else
      {
        logger.log(Level.SEVERE, String.format("Unknown network: %s", network));
      }

    }
    else
    {
      params = new NetworkParamsProd();
    }
  }

  private void loadWidgets()
  {
    ingestor = new BlockIngestor(this);
    forge = new BlockForge(this);

  }

  private void startServices()
    throws Exception
  {
    if (config.isSet("service_port"))
    {
      int port = config.getInt("service_port");

      user_service = new SnowUserService(this);
      Server s = ServerBuilder
        .forPort(port)
        .addService(user_service)
        .build();

      s.start();
    }
  }


  private void loadDB()
    throws Exception
  {
    String db_type = config.get("db_type");
    
    if(db_type.equals("rocksdb"))
    {
      db = new snowblossom.db.rocksdb.JRocksDB(config);    
    }
    else
    {
      logger.log(Level.SEVERE, String.format("Unknown db_type: %s", db_type));
      throw new RuntimeException("Unable to load DB");
    }

    db.open();

  }
  private void loadUtxoDB()
    throws Exception
  {
    config.require("utxo_db_path");
    String utxo_db_path = config.get("utxo_db_path");
    File utxo_db_file = new File(utxo_db_path);
    utxo_db_file.mkdirs();

    utxo_hashed_trie = new HashedTrie(new TrieDBRocks(utxo_db_file),Globals.UTXO_KEY_LEN ,true);
  }


  public Config getConfig(){return config;}
  public DB getDB(){return db;}
  public NetworkParams getParams(){return params;}
  public BlockIngestor getBlockIngestor(){ return ingestor; }
  public BlockForge getBlockForge() {return forge;}
  public HashedTrie getUtxoHashedTrie(){return utxo_hashed_trie;}
}