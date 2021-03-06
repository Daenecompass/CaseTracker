package com.kritsit.casetracker.client.validation;

public class BooleanValidator implements IValidator<Boolean> {
    public boolean validate(Object obj) {
        if (obj == null) {
            return false;
        }
        return obj.getClass() == Boolean.class;
    }
}
