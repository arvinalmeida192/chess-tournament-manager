package com.chess.tournament.dao;

import java.sql.Connection;

@FunctionalInterface
public interface TransactionCallback<T> {

    T doInTransaction(Connection connection) throws Exception;
}
