package com.mac.audit.service;

import com.mac.audit.entities.model.ErrorAlert;

public interface ErrorAlertNotifier {

    void send(ErrorAlert alert);
}
