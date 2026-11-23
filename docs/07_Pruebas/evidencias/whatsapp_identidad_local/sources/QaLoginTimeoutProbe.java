package com.zaxxer.hikari.pool;

import com.zaxxer.hikari.HikariConfig;
import com.mysql.cj.protocol.StandardSocketFactory;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.logging.Logger;

/** Regresión sin red: reproduce el presupuesto del driver con las bibliotecas del JAR QA. */
public final class QaLoginTimeoutProbe extends PoolBase {
    QaLoginTimeoutProbe(HikariConfig c){super(c);}
    @Override void recycle(PoolEntry e){throw new AssertionError("CONNECTION_NOT_ALLOWED");}
    static final class RecordingDataSource implements DataSource {
        int seconds;
        public void setLoginTimeout(int v){seconds=v;}
        public int getLoginTimeout(){return seconds;}
        public Connection getConnection(){throw new AssertionError("NETWORK_NOT_ALLOWED");}
        public Connection getConnection(String u,String p){throw new AssertionError("NETWORK_NOT_ALLOWED");}
        public PrintWriter getLogWriter(){return null;}
        public void setLogWriter(PrintWriter p){}
        public Logger getParentLogger(){return Logger.getAnonymousLogger();}
        public boolean isWrapperFor(Class<?> c){return false;}
        public <T>T unwrap(Class<T> c)throws SQLException{throw new SQLException();}
    }
    static final class Budget extends StandardSocketFactory {
        void check(int seconds)throws java.net.SocketException{
            loginTimeoutCountdown=seconds*1000;
            loginTimeoutCheckTimestamp=System.currentTimeMillis()-4000;
            resetLoginTimeCountdown();
        }
    }
    static int login(int timeout){
        var ds=new RecordingDataSource();var c=new HikariConfig();
        c.setPoolName("QA_TIMEOUT_REGRESSION");c.setConnectionTimeout(timeout);c.setDataSource(ds);
        new QaLoginTimeoutProbe(c);return ds.seconds;
    }
    public static void main(String[] args)throws Exception{
        int old=login(3000),corrected=login(30000);
        if(old!=3||corrected!=30)throw new AssertionError("HIKARI_LOGIN_BUDGET_MISMATCH");
        boolean reproduced=false;
        try{new Budget().check(old);}catch(java.net.SocketException e){
            reproduced="Connection attempt exceeded defined timeout.".equals(e.getMessage());
        }
        if(!reproduced)throw new AssertionError("ORIGINAL_FAILURE_NOT_REPRODUCED");
        new Budget().check(corrected);
        System.out.println("QA_TIMEOUT_REGRESSION_PASS: login 3s rechaza; login 30s conserva plazo finito y permite 4s de handshake");
    }
}
