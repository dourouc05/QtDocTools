#pragma once

#include <list>

class AST;
class Parameter;
class Object;
class Value;

enum ValueType { NONE, BOOLEAN, INTEGER, DOUBLE, STRING, OBJECT, CONSTANT }; // Constant is implemented as string.
typedef union {
	bool b;
	int i;
	double d;
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
	bool isConst = false;
	std::string* type;
	std::string* pointersReferences = nullptr;
	std::string* identifier;
	Value* initialiser = nullptr;

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