#pragma once

#include <list>

class AST;
class Parameter;
class Object;
class Value;

enum ValueType { NONE, BOOLEAN, STRING, OBJECT, CONSTANT }; // Constant and numbers (integers, doubles) are implemented as strings.
typedef union {
	bool b;
	std::string* s;
	Object* o;
} ValueContent;

class AST {
public:
	bool matched;
	std::list<Parameter*> parameters;
	bool isConst = false;

	AST();
	~AST();

	std::string serialise() const;
};

class Parameter {
public: 
	bool volatility = false; 
	bool constnessFront = false;
	bool constnessMiddle = false;
	bool constnessRear = false;
	std::string* type;
	std::string* pointersReferences = nullptr;
	std::string* pointersReferencesAfterRear = nullptr;
	std::string* identifier;
	Value* initialiser = nullptr;
	bool isEllipsis = false; 

	Parameter();
	~Parameter();

	std::string pointersReferencesStr() const; 
	std::string serialise() const;
};

class Object {
public: 
	std::string* identifier;
	std::list<Value*> parameters;

	Object();
	~Object();

	std::string serialise() const;
};

class Value {
public:
	ValueType type;
	ValueContent content;

	Value();
	~Value();

	std::string serialise() const;
};