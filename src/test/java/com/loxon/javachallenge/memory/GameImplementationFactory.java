package com.loxon.javachallenge.memory;

import com.loxon.javachallenge.memory.api.Game;
import com.loxon.javachallenge.memory.api.MemoryState;
import com.loxon.javachallenge.memory.api.Player;
import com.loxon.javachallenge.memory.api.PlayerScore;
import com.loxon.javachallenge.memory.api.communication.commands.*;
import com.loxon.javachallenge.memory.api.communication.general.Command;
import com.loxon.javachallenge.memory.api.communication.general.Response;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;

public class GameImplementationFactory {
    public static Game get() {
        return new Game() {
            private ArrayList<Player> players = new ArrayList<>();
            private List<MemoryState> memory;
            private String[] owners;
            private int rounds;
            private int currentRound = 0;
            
            @Override
            public Player registerPlayer(String name) {
                Player player = new Player(name);
                players.add(player);
                return player;
            }

            @Override
            public void startGame(List<MemoryState> initialMemory, int rounds) {
                memory = initialMemory;
                owners = new String[initialMemory.size()];
                this.rounds = rounds;
            }

            @Override
            public List<Response> nextRound(Command... requests) {
                ArrayList<Response> responses = new ArrayList<>();
                for (Command command : requests) {
                    Player player = command.getPlayer();
                    if (command instanceof CommandStats) {
                        ResponseStats resp = new ResponseStats(player);
                        resp.setCellCount(memory.size());
                        int owned = 0;
                        int free = 0;
                        int allocated = 0;
                        int corrupt = 0;
                        int fortified = 0;
                        int system = 0;
                        for (MemoryState state : memory) {
                            switch (state) {
                                case FREE:
                                    free++;
                                    break;
                                case ALLOCATED:
                                    allocated++;
                                    break;
                                case CORRUPT:
                                    corrupt++;
                                    break;
                                case FORTIFIED:
                                    fortified++;
                                    break;
                                case SYSTEM:
                                    system++;
                                    break;
                            }
                        }
                        for (String owner : owners) {
                            if (owner != null)
                                owned++;
                        }
                        
                        resp.setOwnedCells(owned);
                        resp.setFreeCells(free);
                        resp.setAllocatedCells(allocated);
                        resp.setCorruptCells(corrupt);
                        resp.setFortifiedCells(fortified);
                        resp.setSystemCells(system);
                        resp.setRemainingRounds(rounds - currentRound);
                        responses.add(resp);
                    } else if (command instanceof CommandScan) {
                        int cell = ((CommandScan) command).getCell();
                        ArrayList<MemoryState> states = new ArrayList<>();
                        for (int i = 0; i < 4; i++) {
                            MemoryState state = memory.get(cell + i);
                            if (owners[cell + i].equals(player.getName()))
                                state = state == MemoryState.ALLOCATED ? 
                                        MemoryState.OWNED_ALLOCATED : MemoryState.OWNED_FORTIFIED;
                            states.add(state);
                        }
                        ResponseScan resp = new ResponseScan(player, cell, states);
                        responses.add(resp);
                    } else if (command instanceof CommandAllocate) {
                        List<Integer> cells = ((CommandAllocate) command).getCells();
                        ArrayList<Integer> successCells = new ArrayList<>();
                        for (int cell : cells) {
                            MemoryState state = memory.get(cell);
                            if(state == MemoryState.FREE) {
                                memory.set(cell, MemoryState.ALLOCATED);
                                owners[cell] = player.getName();
                                successCells.add(cell);
                            } else if (state == MemoryState.ALLOCATED) {
                                memory.set(cell, MemoryState.CORRUPT);
                                owners[cell] = null;
                            }
                        }
                        ResponseSuccessList resp = new ResponseSuccessList(player, successCells);
                        responses.add(resp);
                    } else if (command instanceof CommandFree) {
                        List<Integer> cells = ((CommandFree) command).getCells();
                        ArrayList<Integer> successCells = new ArrayList<>();
                        for (int cell : cells) {
                            MemoryState state = memory.get(cell);
                            if (state == MemoryState.ALLOCATED || state == MemoryState.CORRUPT) {
                                memory.set(cell, MemoryState.FREE);
                                owners[cell] = null;
                                successCells.add(cell);
                            }
                        }
                        ResponseSuccessList resp = new ResponseSuccessList(player, successCells);
                        responses.add(resp);
                    } else if (command instanceof CommandRecover) {
                        List<Integer> cells = ((CommandRecover) command).getCells();
                        ArrayList<Integer> successCells = new ArrayList<>();
                        for (int cell : cells) {
                            MemoryState state = memory.get(cell);
                            if (state == MemoryState.CORRUPT) {
                                memory.set(cell, MemoryState.ALLOCATED);
                                owners[cell] = player.getName();
                                successCells.add(cell);
                            } else if (state == MemoryState.ALLOCATED || owners[cell] != null) {
                                memory.set(cell, MemoryState.CORRUPT);
                                owners[cell] = null;
                            }
                        }
                        ResponseSuccessList resp = new ResponseSuccessList(player, successCells);
                        responses.add(resp);
                    } else if (command instanceof CommandFortify) {
                        List<Integer> cells = ((CommandFortify) command).getCells();
                        ArrayList<Integer> successCells = new ArrayList<>();
                        for (int cell : cells) {
                            MemoryState state = memory.get(cell);
                            if (state == MemoryState.ALLOCATED) {
                                memory.set(cell, MemoryState.FORTIFIED);
                                successCells.add(cell);
                            }
                        }
                        ResponseSuccessList resp = new ResponseSuccessList(player, successCells);
                        responses.add(resp);
                    } else if (command instanceof CommandSwap) {
                        List<Integer> cells = ((CommandSwap) command).getCells();
                        ArrayList<Integer> successCells = new ArrayList<>();
                        int cell1 = ((CommandSwap) command).getCells().get(0);
                        int cell2 = ((CommandSwap) command).getCells().get(1);
                        MemoryState state1 = memory.get(cell1);
                        MemoryState state2 = memory.get(cell2);
                        if (state1 != MemoryState.SYSTEM && state1 != MemoryState.FORTIFIED &&
                                state2 != MemoryState.SYSTEM && state2 != MemoryState.FORTIFIED) {
                            String tempName = owners[cell1];
                            owners[cell2] = owners[cell1];
                            owners[cell2] = tempName;
                            memory.set(cell1, state2);
                            memory.set(cell2, state1);
                            successCells.add(cell1);
                            successCells.add(cell2);
                        }
                        ResponseSuccessList resp = new ResponseSuccessList(player, successCells);
                        responses.add(resp);
                    }
                }
                return responses;
            }

            @Override
            public List<PlayerScore> getScores() {
                HashMap<String, PlayerScore> scores = new HashMap<>();
                for (Player player : players) {
                    PlayerScore score = new PlayerScore(player);
                    score.setFortifiedCells(0);
                    score.setOwnedBlocks(0);
                    score.setOwnedCells(0);
                    score.setTotalScore(0);
                    scores.put(player.getName(), score);
                }
                
                for (int i = 0; i < memory.size() / 4; i++) {
                    String firstOwner = owners[i * 4];
                    boolean ownedByTheSame = true;
                    // Blokkon belűl
                    for (int j = 0; j < 4; j++) {
                        String owner = owners[i * 4 + j];
                        if (!owner.equals(firstOwner)) {
                            ownedByTheSame = false;
                        }
                        if (owner != null) {
                            PlayerScore score =  scores.get(owners[i * 4 + j]);
                            switch (memory.get(i * 4 + j)) {
                                case ALLOCATED:
                                    score.setOwnedCells(score.getOwnedCells() + 1);
                                    score.setTotalScore(score.getTotalScore() + 1);
                                    break;
                                case FORTIFIED:
                                    score.setFortifiedCells(score.getFortifiedCells() + 1);
                                    break;
                            }
                        }
                    }
                    if (ownedByTheSame && firstOwner != null) {
                        PlayerScore score =  scores.get(owners[i * 4]);
                        score.setOwnedBlocks(score.getOwnedBlocks() + 1);
                        score.setTotalScore(score.getTotalScore() + 4);
                    }
                }
                return new ArrayList<>(scores.values());
            }

            @Override
            public String visualize() {
                return "";
            }
        };
    }
}