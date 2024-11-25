package com.sskings.shopping_delivery.exceptions;

public class EmailExistenteException extends RuntimeException{
    public EmailExistenteException(String message){
        super(message);
    }
}
