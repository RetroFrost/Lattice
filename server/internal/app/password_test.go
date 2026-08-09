package app

import "testing"

func TestPasswordRoundTrip(t *testing.T) {
	encoded, err := hashPassword("correct horse battery staple")
	if err != nil {
		t.Fatal(err)
	}
	if !verifyPassword("correct horse battery staple", encoded) {
		t.Fatal("correct password was rejected")
	}
	if verifyPassword("wrong password", encoded) {
		t.Fatal("wrong password was accepted")
	}
}

func TestPasswordRejectsGarbage(t *testing.T) {
	if verifyPassword("anything", "not-a-password-hash") {
		t.Fatal("garbage password hash was accepted")
	}
}
