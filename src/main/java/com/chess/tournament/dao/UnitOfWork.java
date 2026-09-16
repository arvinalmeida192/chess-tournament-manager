package com.chess.tournament.dao;

public interface UnitOfWork {

    <T> T executeInTransaction(TransactionCallback<T> callback);
}
