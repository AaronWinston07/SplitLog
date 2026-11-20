package models;

public class Person {



    /**
     * One row of the PEOPLE table.
     * balance is stored in PAISE: positive = they owe me, negative = I owe them.
     */


        private final long id;
        private final String name;
        private final long balance;
        private final long createdAt;

        public Person(long id, String name, long balance, long createdAt) {
            this.id = id;
            this.name = name;
            this.balance = balance;
            this.createdAt = createdAt;
        }

        public long getId() { return id; }
        public String getName() { return name; }
        public long getBalance() { return balance; }
        public long getCreatedAt() { return createdAt; }
    }

